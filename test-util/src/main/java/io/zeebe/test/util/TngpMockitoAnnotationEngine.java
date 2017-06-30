package io.zeebe.test.util;

import static org.mockito.Mockito.withSettings;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;

import org.mockito.Mockito;
import org.mockito.internal.configuration.DefaultAnnotationEngine;

public class TngpMockitoAnnotationEngine extends DefaultAnnotationEngine
{

    @Override
    public Object createMockFor(Annotation annotation, Field field)
    {
        if (annotation instanceof FluentMock)
        {
            return Mockito.mock(field.getType(),
                    withSettings()
                        .name(field.getName())
                        .defaultAnswer(new FluentAnswer()));
        }
        else
        {
            return super.createMockFor(annotation, field);
        }
    }
}
