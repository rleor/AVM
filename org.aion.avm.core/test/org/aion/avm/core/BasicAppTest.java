package org.aion.avm.core;

import java.lang.reflect.Method;
import java.util.function.Function;

import org.aion.avm.arraywrapper.ByteArray;
import org.aion.avm.core.arraywrapping.ArrayWrappingClassGenerator;
import org.aion.avm.core.classloading.AvmClassLoader;
import org.aion.avm.rt.Address;
import org.aion.avm.rt.BlockchainRuntime;
import org.aion.avm.userlib.AionMap;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;


/**
 * As part of issue-77, we want to see what a more typical application can see, from inside our environment.
 * This test operates on BasicAppTestTarget to observe what we are doing, from the inside.
 * Eventually, this will change into a shape where we will use the standard AvmImpl to completely run this
 * life-cycle, but we want to prove that it works, in isolation, before changing its details to account for
 * this design (especially considering that the entry-point interface is likely temporary).
 */
public class BasicAppTest {
    private Class<?> clazz;
    private Method decodeMethod;
    private BlockchainRuntime runtime;

    @Before
    public void setup() throws Exception {
        SimpleRuntime externalRuntime = new SimpleRuntime(new byte[Address.LENGTH], new byte[Address.LENGTH], 10000);
        SimpleAvm avm = new SimpleAvm(externalRuntime, BasicAppTestTarget.class, AionMap.class);
        AvmClassLoader loader = avm.getClassLoader();
        
        Function<String, byte[]> wrapperGenerator = (cName) -> ArrayWrappingClassGenerator.arrayWrappingFactory(cName);
        loader.addHandler(wrapperGenerator);
        
        this.clazz = loader.loadUserClassByOriginalName(BasicAppTestTarget.class.getName());
        // NOTE:  The user's side is pre-shadow so it uses "byte[]" whereas we look up "ByteArray", here.
        this.decodeMethod = this.clazz.getMethod("avm_decode", BlockchainRuntime.class, ByteArray.class);
        Assert.assertEquals(loader, this.clazz.getClassLoader());
        
        // Create the wrapper for the runtime object, now that the external one has been used to create the Helper required to instantiate shadow objects.
        this.runtime = new ContractRuntimeWrapper(externalRuntime);
    }

    @Test
    public void testIdentity() throws Exception {
        ByteArray input = new ByteArray(new byte[] {BasicAppTestTarget.kMethodIdentity, 42, 13});
        ByteArray output = (ByteArray)this.decodeMethod.invoke(null, this.runtime, input);
        // These should be the same instance.
        Assert.assertEquals(input, output);
    }

    @Test
    public void testSumInput() throws Exception {
        ByteArray input = new ByteArray(new byte[] {BasicAppTestTarget.kMethodSum, 42, 13});
        ByteArray output = (ByteArray)this.decodeMethod.invoke(null, this.runtime, input);
        // Should be just 1 byte, containing the sum.
        Assert.assertEquals(1, output.length());
        Assert.assertEquals(BasicAppTestTarget.kMethodSum + 42 + 13, output.get(0));
    }

    @Test
    public void testLowOrderByteArrayHash() throws Exception {
        ByteArray input = new ByteArray(new byte[] {BasicAppTestTarget.kMethodLowOrderByteArrayHash, 42, 13});
        ByteArray output = (ByteArray)this.decodeMethod.invoke(null, this.runtime, input);
        // Should be just 1 byte, containing the low hash byte.
        Assert.assertEquals(1, output.length());
        byte result = output.get(0);
        // This should match the input we gave them.
        Assert.assertEquals(input.avm_hashCode(), result);
    }

    @Test
    public void testLowOrderRuntimeHash() throws Exception {
        ByteArray input = new ByteArray(new byte[] {BasicAppTestTarget.kMethodLowOrderRuntimeHash, 42, 13});
        ByteArray output = (ByteArray)this.decodeMethod.invoke(null, this.runtime, input);
        // Should be just 1 byte, containing the low hash byte.
        Assert.assertEquals(1, output.length());
        byte result = output.get(0);
        // We know that the runtime was the first object we created so its hash will be 1.
        Assert.assertEquals(1, result);
    }

    /**
     * This test makes multiple calls to the same contract instance, proving that static state survives between the calls.
     * It is mostly just a test to make sure that this property continues to be true, in the future, once we decide how
     * to save and resume state.
     */
    @Test
    public void testRepeatedSwaps() throws Exception {
        ByteArray input1 = new ByteArray(new byte[] {BasicAppTestTarget.kMethodSwapInputsFromLastCall, 1});
        ByteArray input2 = new ByteArray(new byte[] {BasicAppTestTarget.kMethodSwapInputsFromLastCall, 2});
        ByteArray output1 = (ByteArray)this.decodeMethod.invoke(null, this.runtime, input1);
        Assert.assertNull(output1);
        ByteArray output2 = (ByteArray)this.decodeMethod.invoke(null, this.runtime, input2);
        Assert.assertEquals(input1.get(1), output2.get(1));
        ByteArray output3 = (ByteArray)this.decodeMethod.invoke(null, this.runtime, input1);
        Assert.assertEquals(input2.get(1), output3.get(1));
    }

    @Test
    public void testArrayEquality() throws Exception {
        ByteArray input = new ByteArray(new byte[] {BasicAppTestTarget.kMethodTestArrayEquality, 42, 13});
        ByteArray output = (ByteArray)this.decodeMethod.invoke(null, this.runtime, input);
        // Should be just 1 byte: 0 (since they should never be equal).
        Assert.assertEquals(1, output.length());
        Assert.assertEquals(0, output.get(0));
    }

    @Test
    public void testAllocateArray() throws Exception {
        ByteArray input = new ByteArray(new byte[] {BasicAppTestTarget.kMethodAllocateObjectArray, 42, 13});
        ByteArray output = (ByteArray)this.decodeMethod.invoke(null, this.runtime, input);
        // Should be just 1 byte: 2 (since that is the length).
        Assert.assertEquals(1, output.length());
        Assert.assertEquals(2, output.get(0));
    }

    @Test
    public void testByteAutoboxing() throws Exception {
        ByteArray input = new ByteArray(new byte[] {BasicAppTestTarget.kMethodByteAutoboxing, 42});
        ByteArray output = (ByteArray)this.decodeMethod.invoke(null, this.runtime, input);
        // Should be just 2 bytes: 2 wrapper hashcode low byte and unwrapped value.
        Assert.assertEquals(2, output.length());
        Assert.assertEquals(42, output.get(0));
        Assert.assertEquals(42, output.get(1));
    }

    @Test
    public void testMapInteraction() throws Exception {
        ByteArray input = new ByteArray(new byte[] {BasicAppTestTarget.kMethodMapPut, 1, 42});
        ByteArray output = (ByteArray)this.decodeMethod.invoke(null, this.runtime, input);
        Assert.assertEquals(1, output.length());
        Assert.assertEquals(42, output.get(0));
        
        input = new ByteArray(new byte[] {BasicAppTestTarget.kMethodMapPut, 2, 13});
        output = (ByteArray)this.decodeMethod.invoke(null, this.runtime, input);
        Assert.assertEquals(1, output.length());
        Assert.assertEquals(13, output.get(0));
        
        input = new ByteArray(new byte[] {BasicAppTestTarget.kMethodMapGet, 2});
        output = (ByteArray)this.decodeMethod.invoke(null, this.runtime, input);
        Assert.assertEquals(1, output.length());
        Assert.assertEquals(13, output.get(0));
    }
}