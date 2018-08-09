package com.example.helloworld;

import org.aion.avm.api.ABIDecoder;
import org.aion.avm.api.ABIEncoder;
import org.aion.avm.api.InvalidTxDataException;
import org.aion.avm.api.BlockchainRuntime;

public class HelloWorld {

    private int foo;

    private static int bar;

    static {
        if (BlockchainRuntime.getData() != null) {
            Object[] arguments = ABIDecoder.decodeArguments(BlockchainRuntime.getData());
            bar = (int)arguments[0];
        }
    }

    public int add(int a, int b) {
        return a + b;
    }

    public byte[] run() {
        return "Hello, world!".getBytes();
    }

    public static byte[] main() throws InvalidTxDataException {
        return ABIDecoder.decodeAndRun(new HelloWorld(), BlockchainRuntime.getData());
    }
}
