package org.aion.avm.core;

import org.aion.avm.core.exceptionwrapping.ExceptionWrapping;
import org.aion.avm.core.instrument.ClassMetering;
import org.aion.avm.core.shadowing.ClassShadowing;
import org.aion.avm.core.stacktracking.StackTracking;
import org.aion.avm.core.util.ClassHierarchyForest;
import org.aion.avm.rt.BlockchainRuntime;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;

import java.io.File;
import java.util.Collections;
import java.util.Map;

public class AvmImpl implements Avm {

    /**
     * Extracts the DApp module in compressed format into the designated folder.
     *
     * @param module     the DApp module in JAR format
     * @param tempFolder the temporary folder where bytecode should be stored
     * @return the main class name if this operation is successful, otherwise null
     */
    public String extract(byte[] module, File tempFolder) {

        // TODO: Rom

        return null;
    }

    /**
     * Loads the module into memory.
     *
     * @param tempFolder the temporary folder containing all the classes
     * @return a map between class name and bytecode
     */
    public Map<String, byte[]> load(File tempFolder) {

        // TODO: Rom

        return null;
    }


    /**
     * Validates all classes, including but not limited to:
     *
     * <ul>
     * <li>class format (hash, version, etc.)</li>
     * <li>no native method</li>
     * <li>no invalid opcode</li>
     * <li>package name does not start with <code>org.aion</code></li>
     * <li>TODO: add more</li>
     * </ul>
     *
     * @param classes        the classes of DApp
     * @param classHierarchy the containers which stores the inheritance info
     * @return true if the classes are valid, otherwise false
     */
    public boolean validateClasses(Map<String, byte[]> classes, ClassHierarchyForest classHierarchy) {

        // TODO: Rom

        return false;
    }

    /**
     * Returns the sizes of all the classes provided.
     *
     * @param classes        the class of DApp
     * @param classHierarchy the class hierarchy
     * @return a mapping between class name and object size
     */
    public Map<String, Integer> calculateObjectSize(Map<String, byte[]> classes, ClassHierarchyForest classHierarchy) {

        // TODO: Nancy

        return Collections.emptyMap();
    }

    /**
     * Replaces the <code>java.base</code> package with the shadow implementation.
     *
     * @param classes        the class of DApp
     * @param classHierarchy the class hierarchy
     * @param objectSizes    the sizes of object
     * @return the classes after
     */
    public Map<String, byte[]> analyzeClasses(Map<String, byte[]> classes, ClassHierarchyForest classHierarchy, Map<String, Integer> objectSizes ) {

        // TODO: Yulong

        for (String name : classes.keySet()) {

            ClassReader in = new ClassReader(classes.get(name));

            // in reverse order
            ClassWriter out = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
            StackTracking stackTracking = new StackTracking(out);
            ExceptionWrapping exceptionHandling = new ExceptionWrapping(stackTracking);
            ClassShadowing classShadowing = new ClassShadowing(exceptionHandling);
            ClassMetering classMetering = new ClassMetering(classShadowing, classHierarchy, objectSizes);

            // traverse
            in.accept(classMetering, ClassReader.SKIP_DEBUG);
        }


        return null;
    }

    /**
     * Stores the instrumented bytecode into database.
     *
     * @param address   the address of the DApp
     * @param mainClass the mainclasss
     * @param classes   the instrumented bytecode
     */
    public void storeClasses(String address, String mainClass, Map<String, byte[]> classes) {

        // TODO: Rom
    }

    @Override
    public boolean deploy(byte[] code) {
        // STEP-1: compute the hash of the code, which will be used as identifier

        // STEP-2: extract the classes to a temporary folder

        // STEP-3: walk through all the classes and inject metering code

        // STEP-4: store the instrumented code and metadata(e.g. main class name)

        return false;
    }

    @Override
    public AvmResult run(byte[] codeHash, BlockchainRuntime rt) {
        // STEP-1: retrieve the instrumented bytecode using the given codeHash

        // STEP-2: load the classed. class loading fees should apply during the process

        // STEP-3: invoke the `run` method of the main class

        // STEP-4: return the DApp output


        return null;
    }
}