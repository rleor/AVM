package org.aion.avm.core.persistence;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.util.IdentityHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.function.Function;

import org.aion.avm.internal.IDeserializer;
import org.aion.avm.internal.RuntimeAssertionError;


/**
 * Similar to the ReflectionStructureCodec but only used for reentrant DApp invocation.
 * Specifically, knows how to hold an old version of the data in class statics and create a new object graph which lazily loads from the old
 * one.
 * Note that instance relationships are very important for this component.  Specifically, an instance in the caller can NEVER be replaced by
 * a new callee instance (since it might be referenced from the stack, for example).  This also means that an association between
 * callee-instances and caller-instances must be maintained (either within the instances, themselves, or in a side identity map) so that
 * changes can be written-back.
 * 
 * See issue-167 for more information.
 * TODO:  Investigate possible ways to generalize all field walkers found here and in ReflectionStructureCodec.
 */
public class ReentrantGraphProcessor implements IDeserializer, LoopbackCodec.AutomaticSerializer, LoopbackCodec.AutomaticDeserializer {
    /**
     * We apply the DONE_MARKER to a callee object when we determine it (or its corresponding caller) has been added to a queue to process.
     * This is used to mark the object so we don't add it to the queue more than once.
     * We only mark the callee objects since we want a consistent convention and fewer of them require cleanup.
     * Note that the DONE_MARKER MUST be removed from any objects which remain in the live graph or they may be missed in later
     * serialization attempts.
     */
    private static IDeserializer DONE_MARKER = new IDeserializer() {
        @Override
        public void startDeserializeInstance(org.aion.avm.shadow.java.lang.Object instance, long instanceId) {
        }};

    // NOTE:  This fieldCache is passed in from outside so we can modify it for later use (it is used for multiple instances of this).
    private final ReflectedFieldCache fieldCache;
    private final IStorageFeeProcessor feeProcessor;
    private final List<Class<?>> classes;
    
    // We need bidirectional identity maps:
    // -callee->caller for deserializing a callee object - it needs to lookup the caller source (although this could be managed by a field in the object).
    // -caller->callee for uniquing instance stubs (they don't have IDs but are looked up by instance, directly).
    private final IdentityHashMap<org.aion.avm.shadow.java.lang.Object, org.aion.avm.shadow.java.lang.Object> calleeToCallerMap;
    private final IdentityHashMap<org.aion.avm.shadow.java.lang.Object, org.aion.avm.shadow.java.lang.Object> callerToCalleeMap;
    
    // We only hold the deserializerField because we need to check if it is null when traversing the graph for objects to serialize.
    private final Field deserializerField;
    private final Field instanceIdField;
    
    // (mostly non-final just to prove that the state machine is being used correctly).
    private Queue<Object> previousStatics;

    public ReentrantGraphProcessor(ReflectedFieldCache fieldCache, IStorageFeeProcessor feeProcessor, List<Class<?>> classes) {
        this.fieldCache = fieldCache;
        this.feeProcessor = feeProcessor;
        this.classes = classes;
        
        this.calleeToCallerMap = new IdentityHashMap<>();
        this.callerToCalleeMap = new IdentityHashMap<>();
        
        try {
            this.deserializerField = org.aion.avm.shadow.java.lang.Object.class.getDeclaredField("deserializer");
            this.deserializerField.setAccessible(true);
            this.instanceIdField = org.aion.avm.shadow.java.lang.Object.class.getDeclaredField("instanceId");
            this.instanceIdField.setAccessible(true);
        } catch (NoSuchFieldException | SecurityException e) {
            // This would be a serious mis-configuration.
            throw RuntimeAssertionError.unexpected(e);
        }
    }

    /**
     * Called at the beginning of a reentrant call.
     * This copies the existing statics (including representing roots to the existing object graph they describe) to a back-buffer and replaces
     * all existing object references in the statics with instance stubs which back-end on the original versions.
     */
    public void captureAndReplaceStaticState() {
        // The internal mapping structures must be empty.
        RuntimeAssertionError.assertTrue(this.calleeToCallerMap.isEmpty());
        RuntimeAssertionError.assertTrue(this.callerToCalleeMap.isEmpty());
        
        try {
            internalCaptureAndReplaceStaticState();
        } catch (IllegalArgumentException | IllegalAccessException | InstantiationException | InvocationTargetException | NoSuchMethodException | SecurityException e) {
            // Any failures at this point are either failure mis-configuration or serious bugs in our implementation.
            RuntimeAssertionError.unexpected(e);
        }
    }

    private void internalCaptureAndReplaceStaticState() throws IllegalArgumentException, IllegalAccessException, InstantiationException, InvocationTargetException, NoSuchMethodException, SecurityException {
        // We will save out and build new stubs for references in the same pass.
        Queue<Object> inOrderData = new LinkedList<>();
        for (Class<?> clazz : this.classes) {
            for (Field field : this.fieldCache.getDeclaredFieldsForClass(clazz)) {
                // We are only capturing class statics.
                if (Modifier.STATIC == (Modifier.STATIC & field.getModifiers())) {
                    Class<?> type = field.getType();
                    if (boolean.class == type) {
                        boolean val = field.getBoolean(null);
                        inOrderData.add(val);
                    } else if (byte.class == type) {
                        byte val = field.getByte(null);
                        inOrderData.add(val);
                    } else if (short.class == type) {
                        short val = field.getShort(null);
                        inOrderData.add(val);
                    } else if (char.class == type) {
                        char val = field.getChar(null);
                        inOrderData.add(val);
                    } else if (int.class == type) {
                        int val = field.getInt(null);
                        inOrderData.add(val);
                    } else if (float.class == type) {
                        float val = field.getFloat(null);
                        inOrderData.add(val);
                    } else if (long.class == type) {
                        long val = field.getLong(null);
                        inOrderData.add(val);
                    } else if (double.class == type) {
                        double val = field.getDouble(null);
                        inOrderData.add(val);
                    } else {
                        // This should be a shadow object.
                        org.aion.avm.shadow.java.lang.Object contents = (org.aion.avm.shadow.java.lang.Object)field.get(null);
                        inOrderData.add(contents);
                        if (null != contents) {
                            // We now want to replace this object with a stub which knows how to deserialize itself from contents.
                            org.aion.avm.shadow.java.lang.Object stub = internalGetCalleeStubForCaller(contents);
                            field.set(null, stub);
                        }
                    }
                }
            }
        }
        RuntimeAssertionError.assertTrue(null == this.previousStatics);
        this.previousStatics = inOrderData;
    }

    /**
     * Called after a reentrant call finishes in an error state and must be reverted.
     * This discards the current graph and over-writes all class statics with the contents of the back-buffer.
     */
    public void revertToStoredFields() {
        try {
            internalRevertToStoredFields();
        } catch (IllegalArgumentException | IllegalAccessException e) {
            // Any failures at this point are either failure mis-configuration or serious bugs in our implementation.
            RuntimeAssertionError.unexpected(e);
        }
    }

    private void internalRevertToStoredFields() throws IllegalArgumentException, IllegalAccessException {
        // This is the simple case:  walk the previous statics and over-write them with the versions we stored, originally.
        RuntimeAssertionError.assertTrue(null != this.previousStatics);
        
        for (Class<?> clazz : this.classes) {
            for (Field field : this.fieldCache.getDeclaredFieldsForClass(clazz)) {
                // We are only capturing class statics.
                if (Modifier.STATIC == (Modifier.STATIC & field.getModifiers())) {
                    Class<?> type = field.getType();
                    if (boolean.class == type) {
                        boolean val = (Boolean)this.previousStatics.remove();
                        field.setBoolean(null, val);
                    } else if (byte.class == type) {
                        byte val = (Byte)this.previousStatics.remove();
                        field.setByte(null, val);
                    } else if (short.class == type) {
                        short val = (Short)this.previousStatics.remove();
                        field.setShort(null, val);
                    } else if (char.class == type) {
                        char val = (Character)this.previousStatics.remove();
                        field.setChar(null, val);
                    } else if (int.class == type) {
                        int val = (Integer)this.previousStatics.remove();
                        field.setInt(null, val);
                    } else if (float.class == type) {
                        float val = (Float)this.previousStatics.remove();
                        field.setFloat(null, val);
                    } else if (long.class == type) {
                        long val = (Long)this.previousStatics.remove();
                        field.setLong(null, val);
                    } else if (double.class == type) {
                        double val = (Double)this.previousStatics.remove();
                        field.setDouble(null, val);
                    } else {
                        // This should be a shadow object.
                        org.aion.avm.shadow.java.lang.Object val = (org.aion.avm.shadow.java.lang.Object)this.previousStatics.remove();
                        field.set(null, val);
                    }
                }
            }
        }
        this.previousStatics = null;
    }

    /**
     * Called after a reentrant call finishes in a success state and should be committed.
     * This considers the current graph as "correct" but prefers the object instances in the caller's graph (rooted in the back-buffer).
     * This is because the caller could still have things like stack slots which point at these older instances so we have to treat that
     * graph as canonical.
     * This disagreement is rationalized by copying the contents of each of the callee's objects into the corresponding caller's
     * instances.
     */
    public void commitGraphToStoredFieldsAndRestore() {
        try {
            internalCommitGraphToStoredFieldsAndRestore();
        } catch (IllegalArgumentException | IllegalAccessException e) {
            // Any failures at this point are either failure mis-configuration or serious bugs in our implementation.
            RuntimeAssertionError.unexpected(e);
        }
    }

    private void internalCommitGraphToStoredFieldsAndRestore() throws IllegalArgumentException, IllegalAccessException {
        // This is the complicated case:  walk the previous statics, writing only the instances back but updating each of those instances written to
        // the graph with the callee version's contents, recursively.
        // In any cases where a reference was to be copied, choose the caller version unless there isn't one, in which case the callee version can be
        // copied (new object case).  In either case, the recursive update of the graph must be continued through this newly-attached object.
        RuntimeAssertionError.assertTrue(null != this.previousStatics);
        // We discard the statics since the only information we need from them (the caller instances from which the callees are derived) is already
        // in our calleeToCallerMap.
        this.previousStatics = null;
        
        // First, walk all the statics and start seeding our work.
        Queue<org.aion.avm.shadow.java.lang.Object> calleeObjectsToProcess = new LinkedList<>();
        for (Class<?> clazz : this.classes) {
            for (Field field : this.fieldCache.getDeclaredFieldsForClass(clazz)) {
                // We are only capturing class statics.
                if (Modifier.STATIC == (Modifier.STATIC & field.getModifiers())) {
                    Class<?> type = field.getType();
                    if (boolean.class == type) {
                    } else if (byte.class == type) {
                    } else if (short.class == type) {
                    } else if (char.class == type) {
                    } else if (int.class == type) {
                    } else if (float.class == type) {
                    } else if (long.class == type) {
                    } else if (double.class == type) {
                    } else {
                        // Load the field (it will be either a new object or the callee-space object which we need to replace with its caller-space).
                        org.aion.avm.shadow.java.lang.Object callee = (org.aion.avm.shadow.java.lang.Object)field.get(null);
                        // See if there is a caller version.
                        org.aion.avm.shadow.java.lang.Object caller = mapCalleeToCallerAndEnqueueForCommitProcessing(calleeObjectsToProcess, callee);
                        // If there was a caller version, copy this back (otherwise, we will continue looking at the callee).
                        if (null != caller) {
                            field.set(null, caller);
                        }
                    }
                }
            }
        }
        
        // Now that the statics are processed, we can process the queue until it is empty (this will complete the graph).
        // (we also need to remember which "new object" instances need to have their DONE_MARKER cleared - must be done in a second phase).
        Queue<org.aion.avm.shadow.java.lang.Object> placeholdersToUnset = new LinkedList<>();
        while (!calleeObjectsToProcess.isEmpty()) {
            org.aion.avm.shadow.java.lang.Object calleeSpaceToProcess = calleeObjectsToProcess.remove();
            
            // NOTE:  Due to the above-mentioned DONE_MARKER restrictions, this is always a callee-space object but we usually want the caller-space instance.
            org.aion.avm.shadow.java.lang.Object callerSpaceCounterpart = this.calleeToCallerMap.get(calleeSpaceToProcess);
            
            // We want to copy "back" to the caller space so serialize the callee and deserialize them into either the caller or callee (if there was no caller).
            // (the reason to serialize/deserialize the same object is to process the object references in a general way:  try to map back into the caller space).
            Function<org.aion.avm.shadow.java.lang.Object, org.aion.avm.shadow.java.lang.Object> deserializeHelper = (calleeField) -> mapCalleeToCallerAndEnqueueForCommitProcessing(calleeObjectsToProcess, calleeField);
            LoopbackCodec loopback = new LoopbackCodec(this, this, deserializeHelper);
            // Serialize the callee-space object.
            calleeSpaceToProcess.serializeSelf(null, loopback);
            
            if (null != callerSpaceCounterpart) {
                // Deserialize into the caller-space object.
                callerSpaceCounterpart.deserializeSelf(null, loopback);
            } else {
                // This means that the callee object is being stitched into the caller graph as a new object.
                // We want need to update any object references which may point back at older caller objects.
                calleeSpaceToProcess.deserializeSelf(null, loopback);
                // We will need to remove the placeholder since that could confuse later serialization (placeholder is only set of the callee instances).
                placeholdersToUnset.add(calleeSpaceToProcess);
            }
            // Prove that we didn't miss anything.
            loopback.verifyDone();
        }
        
        // Finally, clear the markers so we can return.
        while (!placeholdersToUnset.isEmpty()) {
            org.aion.avm.shadow.java.lang.Object calleeSpaceToClear = placeholdersToUnset.remove();
            this.deserializerField.set(calleeSpaceToClear, null);
        }
    }

    @Override
    public void startDeserializeInstance(org.aion.avm.shadow.java.lang.Object instance, long instanceId) {
        // All the objects we are creating to deserialize in the callee space have Long.MIN_VALUE as the instanceId.
        RuntimeAssertionError.assertTrue(Long.MIN_VALUE == instanceId);
        
        // Look up the corresponding caller instance.
        org.aion.avm.shadow.java.lang.Object caller = this.calleeToCallerMap.get(instance);
        // Make sure that it is loaded.
        caller.lazyLoad();
        
        // We want to use the same codec logic which exists in all shadow objects (since the shadow and API classes do special things here which we don't want to duplicate).
        // In this case, we want to provide an object deserialization helper which can create a callee-space instance stub.
        Function<org.aion.avm.shadow.java.lang.Object, org.aion.avm.shadow.java.lang.Object> deserializeHelper = (callerField) -> internalGetCalleeStubForCaller(callerField);
        LoopbackCodec loopback = new LoopbackCodec(this, this, deserializeHelper);
        // Serialize the original.
        caller.serializeSelf(null, loopback);
        // Deserialize this data into the new instance.
        instance.deserializeSelf(null, loopback);
        // Prove that we didn't miss anything.
        loopback.verifyDone();
    }

    @Override
    public void partiallyAutoSerialize(Queue<Object> dataQueue, org.aion.avm.shadow.java.lang.Object instance, Class<?> firstManualClass) {
        try {
            hierarchyAutoSerialize(dataQueue, instance.getClass(), instance, firstManualClass);
        } catch (IllegalArgumentException | IllegalAccessException | ClassNotFoundException | InstantiationException | InvocationTargetException | NoSuchMethodException | SecurityException e) {
            RuntimeAssertionError.unexpected(e);
        }
    }

    @Override
    public void partiallyAutoDeserialize(Queue<Object> dataQueue, Function<org.aion.avm.shadow.java.lang.Object, org.aion.avm.shadow.java.lang.Object> deserializeHelper, org.aion.avm.shadow.java.lang.Object instance, Class<?> firstManualClass) {
        try {
            hierarchyAutoDeserialize(dataQueue, deserializeHelper, instance.getClass(), instance, firstManualClass);
        } catch (IllegalArgumentException | IllegalAccessException | ClassNotFoundException | InstantiationException | InvocationTargetException | NoSuchMethodException | SecurityException e) {
            RuntimeAssertionError.unexpected(e);
        }
    }

    @Override
    public org.aion.avm.shadow.java.lang.Object wrapAsStub(org.aion.avm.shadow.java.lang.Object original) {
        try {
            return internalGetCalleeStubForCaller(original);
        } catch (IllegalArgumentException | SecurityException e) {
            throw RuntimeAssertionError.unexpected(e);
        }
    }


    private void hierarchyAutoSerialize(Queue<Object> dataQueue, Class<?> clazz, org.aion.avm.shadow.java.lang.Object object, Class<?> firstManualClass) throws IllegalArgumentException, IllegalAccessException, ClassNotFoundException, InstantiationException, InvocationTargetException, NoSuchMethodException, SecurityException {
        // This method is recursive since we are looking to find the root where we need to begin:
        // -for Objects this is the shadow Object
        // -for interfaces, it is when we hit null (since they have no super-class but the interface may have statics)
        boolean isAtTop = (org.aion.avm.shadow.java.lang.Object.class == clazz)
                || (null == clazz);
        if (isAtTop) {
            // This is the root so we want to terminate here.
            // There are no statics in this class and we have no automatic decoding of any of its instance variables.
        } else if (clazz == firstManualClass) {
            // We CANNOT deserialize this, since it is the first manual class, but the next invocation can, so pass null as the manual class to them.
            hierarchyAutoSerialize(dataQueue, clazz.getSuperclass(), object, null);
        } else {
            // Call the superclass and serialize this class.
            hierarchyAutoSerialize(dataQueue, clazz.getSuperclass(), object, firstManualClass);
            // If we got null as the first manual class, we can automatically deserialize.
            if (null == firstManualClass) {
                autoSerializeDeclaredFields(dataQueue, clazz, object);
            }
        }
    }

    private void hierarchyAutoDeserialize(Queue<Object> dataQueue, Function<org.aion.avm.shadow.java.lang.Object, org.aion.avm.shadow.java.lang.Object> deserializeHelper, Class<?> clazz, org.aion.avm.shadow.java.lang.Object object, Class<?> firstManualClass) throws IllegalArgumentException, IllegalAccessException, ClassNotFoundException, InstantiationException, InvocationTargetException, NoSuchMethodException, SecurityException {
        // This method is recursive since we are looking to find the root where we need to begin:
        // -for Objects this is the shadow Object
        // -for interfaces, it is when we hit null (since they have no super-class but the interface may have statics)
        boolean isAtTop = (org.aion.avm.shadow.java.lang.Object.class == clazz)
                || (null == clazz);
        if (isAtTop) {
            // This is the root so we want to terminate here.
            // There are no statics in this class and we have no automatic decoding of any of its instance variables.
        } else if (clazz == firstManualClass) {
            // We CANNOT deserialize this, since it is the first manual class, but the next invocation can, so pass null as the manual class to them.
            hierarchyAutoDeserialize(dataQueue, deserializeHelper, clazz.getSuperclass(), object, null);
        } else {
            // Call the superclass and serialize this class.
            hierarchyAutoDeserialize(dataQueue, deserializeHelper, clazz.getSuperclass(), object, firstManualClass);
            // If we got null as the first manual class, we can automatically deserialize.
            if (null == firstManualClass) {
                autoDeserializeDeclaredFields(dataQueue, deserializeHelper, clazz, object);
            }
        }
    }

    private void autoSerializeDeclaredFields(Queue<Object> dataQueue, Class<?> clazz, org.aion.avm.shadow.java.lang.Object object) throws IllegalArgumentException, IllegalAccessException, ClassNotFoundException, InstantiationException, InvocationTargetException, NoSuchMethodException, SecurityException {
        for (Field field : this.fieldCache.getDeclaredFieldsForClass(clazz)) {
            if (0x0 == (Modifier.STATIC & field.getModifiers())) {
                Class<?> type = field.getType();
                if (boolean.class == type) {
                    boolean val = field.getBoolean(object);
                    dataQueue.add(val);
                } else if (byte.class == type) {
                    byte val = field.getByte(object);
                    dataQueue.add(val);
                } else if (short.class == type) {
                    short val = field.getShort(object);
                    dataQueue.add(val);
                } else if (char.class == type) {
                    char val = field.getChar(object);
                    dataQueue.add(val);
                } else if (int.class == type) {
                    int val = field.getInt(object);
                    dataQueue.add(val);
                } else if (float.class == type) {
                    float val = field.getFloat(object);
                    dataQueue.add(val);
                } else if (long.class == type) {
                    long val = field.getLong(object);
                    dataQueue.add(val);
                } else if (double.class == type) {
                    double val = field.getDouble(object);
                    dataQueue.add(val);
                } else {
                    org.aion.avm.shadow.java.lang.Object ref = (org.aion.avm.shadow.java.lang.Object) field.get(object);
                    dataQueue.add(ref);
                }
            }
        } 
    }

    private void autoDeserializeDeclaredFields(Queue<Object> dataQueue, Function<org.aion.avm.shadow.java.lang.Object, org.aion.avm.shadow.java.lang.Object> deserializeHelper, Class<?> clazz, org.aion.avm.shadow.java.lang.Object object) throws IllegalArgumentException, IllegalAccessException, ClassNotFoundException, InstantiationException, InvocationTargetException, NoSuchMethodException, SecurityException {
        for (Field field : this.fieldCache.getDeclaredFieldsForClass(clazz)) {
            if (0x0 == (Modifier.STATIC & field.getModifiers())) {
                Class<?> type = field.getType();
                if (boolean.class == type) {
                    boolean val = (Boolean)dataQueue.remove();
                    field.setBoolean(object, val);
                } else if (byte.class == type) {
                    byte val = (Byte)dataQueue.remove();
                    field.setByte(object, val);
                } else if (short.class == type) {
                    short val = (Short)dataQueue.remove();
                    field.setShort(object, val);
                } else if (char.class == type) {
                    char val = (Character)dataQueue.remove();
                    field.setChar(object, val);
                } else if (int.class == type) {
                    int val = (Integer)dataQueue.remove();
                    field.setInt(object, val);
                } else if (float.class == type) {
                    float val = (Float)dataQueue.remove();
                    field.setFloat(object, val);
                } else if (long.class == type) {
                    long val = (Long)dataQueue.remove();
                    field.setLong(object, val);
                } else if (double.class == type) {
                    double val = (Double)dataQueue.remove();
                    field.setDouble(object, val);
                } else {
                    org.aion.avm.shadow.java.lang.Object val = (org.aion.avm.shadow.java.lang.Object) dataQueue.remove();
                    org.aion.avm.shadow.java.lang.Object mapped = deserializeHelper.apply(val);
                    field.set(object, mapped);
                }
            }
        } 
    }

    private org.aion.avm.shadow.java.lang.Object internalGetCalleeStubForCaller(org.aion.avm.shadow.java.lang.Object caller) {
        // First, see if we already have a stub for this caller.
        org.aion.avm.shadow.java.lang.Object callee = this.callerToCalleeMap.get(caller);
        if (null == callee) {
            // Note that this instanceId will never be used, so we pass in Long.MIN_VALUE.  This is because we never replace caller instances with
            // callee instance.
            // This means that, since this object is the callee representation of a caller object, it will never end up in the caller's graph, hence
            // never serialized to the storage.  The only objects which can be added to the caller's graph are new objects (which don't have an
            // instanceId, either).
            try {
                callee = caller.getClass().getConstructor(IDeserializer.class, long.class).newInstance(this, Long.MIN_VALUE);
            } catch (InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException | NoSuchMethodException | SecurityException e) {
                // TODO:  These should probably come through a cache.
                RuntimeAssertionError.unexpected(e);
            }
            
            // We also need to add this to the callee-caller mapping (in the future, this might be made into a field in the object).
            this.callerToCalleeMap.put(caller, callee);
            this.calleeToCallerMap.put(callee, caller);
        }
        return callee;
    }

    private org.aion.avm.shadow.java.lang.Object mapCalleeToCallerAndEnqueueForCommitProcessing(Queue<org.aion.avm.shadow.java.lang.Object> calleeObjectsToProcess, org.aion.avm.shadow.java.lang.Object calleeSpace) {
        org.aion.avm.shadow.java.lang.Object callerSpace = null;
        if (null != calleeSpace) {
            // We want to replace this with a reference to caller-space (unless this object is new - has no mapping).
            callerSpace = this.calleeToCallerMap.get(calleeSpace);
            
            // NOTE:  We can't store the DONE_MARKER in any caller-space objects since they might have a real deserializer.
            // This means that we need to actually enqueue the callee-space object and, during processing, determine if we need to
            // actually operate on the corresponding caller (essentially determinine which copy is in the output graph).
            try {
                if (null == this.deserializerField.get(calleeSpace)) {
                    // This is either new or was faulted so add this to our queue to process.
                    calleeObjectsToProcess.add(calleeSpace);
                    // Set the market so we don't double-add it.
                    this.deserializerField.set(calleeSpace, DONE_MARKER);
                }
            } catch (IllegalArgumentException | IllegalAccessException e) {
                RuntimeAssertionError.unexpected(e);
            }
        }
        return callerSpace;
    }
}