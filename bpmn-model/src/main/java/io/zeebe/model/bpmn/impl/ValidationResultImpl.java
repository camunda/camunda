package io.zeebe.model.bpmn.impl;

import java.util.ArrayList;
import java.util.List;

import io.zeebe.model.bpmn.ValidationResult;
import io.zeebe.model.bpmn.impl.instance.BaseElement;

public class ValidationResultImpl implements ValidationResult
{
    private static final String MESSAGE = "[%s] [line:%s] (%s) %s";

    private final List<Entry> errors = new ArrayList<>();
    private final List<Entry> warnings = new ArrayList<>();

    public void addError(Object element, String message)
    {
        errors.add(new Entry(message, element));
    }

    public void addWarning(Object element, String message)
    {
        warnings.add(new Entry(message, element));
    }

    @Override
    public boolean hasErrors()
    {
        return !errors.isEmpty();
    }

    @Override
    public boolean hasWarnings()
    {
        return !warnings.isEmpty();
    }

    @Override
    public String toString()
    {
        final StringBuilder builder = new StringBuilder();

        for (Entry error : errors)
        {
            builder.append(String.format(MESSAGE, "ERROR", getLine(error.element), getElementName(error.element), error.message));
            builder.append("\n");
        }

        for (Entry warning : warnings)
        {
            builder.append(String.format(MESSAGE, "WARNING", getLine(warning.element), getElementName(warning.element), warning.message));
            builder.append("\n");
        }

        return builder.toString();
    }

    private String getElementName(Object element)
    {
        String name = "unknown";
        if (element instanceof BaseElement)
        {
            final BaseElement baseElement = (BaseElement) element;
            final String elementName = baseElement.getElementName();
            final String namespace = baseElement.getNamespace();
            if (elementName != null)
            {
                name = namespace + ":" + elementName;
            }
        }
        return name;
    }

    private String getLine(Object element)
    {
        String line = "unknown";
        if (element instanceof BaseElement)
        {
            final BaseElement baseElement = (BaseElement) element;
            final Integer lineNumber = baseElement.getLineNumber();
            if (lineNumber != null)
            {
                line = String.valueOf(lineNumber);
            }
        }
        return line;
    }

    private class Entry
    {
        private String message;
        private Object element;

        Entry(String message, Object element)
        {
            this.message = message;
            this.element = element;
        }
    }

}
