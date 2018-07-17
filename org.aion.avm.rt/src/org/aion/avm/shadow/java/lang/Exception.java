package org.aion.avm.shadow.java.lang;

import org.aion.avm.internal.IHelper;


/**
 * Our shadow implementation of java.lang.Exception.
 * 
 * This only exists as an intermediary since we needed to implement a few specific subclasses.
 */
public class Exception extends Throwable {
    static {
        // Shadow classes MUST be loaded during bootstrap phase.
        IHelper.currentContractHelper.get().externalBootstrapOnly();
    }

    public Exception() {
        super();
    }

    public Exception(String message) {
        super(message);
    }

    public Exception(String message, Throwable cause) {
        super(message, cause);
    }

    public Exception(Throwable cause) {
        super(cause);
    }
}
