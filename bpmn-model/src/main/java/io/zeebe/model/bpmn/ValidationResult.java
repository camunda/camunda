package io.zeebe.model.bpmn;

public interface ValidationResult
{

    boolean hasErrors();

    boolean hasWarnings();

}