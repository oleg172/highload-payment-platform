package dev.fincore.payment.common.exception;

public class EntityNotFoundException extends RuntimeException {
    public EntityNotFoundException(String entityName) {
        super(entityName + " not found");
    }

    public EntityNotFoundException(String entityName, String id) {
        super(entityName + " with id [" + id + "] not found");
    }
}
