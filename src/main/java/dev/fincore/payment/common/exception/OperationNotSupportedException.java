package dev.fincore.payment.common.exception;

public class OperationNotSupportedException extends RuntimeException {

    public OperationNotSupportedException(String msg) {
        super(msg);
    }
}
