/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.zeebe.test.util;

import static org.mockito.Mockito.withSettings;
import static org.mockito.internal.util.collections.Sets.newMockSafeHashSet;
import static org.mockito.internal.util.reflection.FieldSetter.setField;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.Set;

import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.exceptions.base.MockitoException;
import org.mockito.internal.configuration.DefaultInjectionEngine;
import org.mockito.internal.configuration.IndependentAnnotationEngine;
import org.mockito.internal.configuration.SpyAnnotationEngine;
import org.mockito.internal.configuration.injection.scanner.InjectMocksScanner;
import org.mockito.internal.configuration.injection.scanner.MockScanner;
import org.mockito.plugins.AnnotationEngine;

/**
 * Copy of {@link org.mockito.internal.configuration.InjectingAnnotationEngine} extended to support {@link FluentMock}.
 */
public class ZeebeMockitoAnnotationEngine implements AnnotationEngine, org.mockito.configuration.AnnotationEngine
{
    private final AnnotationEngine delegate = new IndependentAnnotationEngine();
    private final AnnotationEngine spyAnnotationEngine = new SpyAnnotationEngine();

    /**
     * Process the fields of the test instance and create Mocks, Spies, Captors and inject them on fields
     * annotated &#64;InjectMocks.
     *
     * <p>
     * This code process the test class and the super classes.
     * <ol>
     * <li>First create Mocks, Spies, Captors.</li>
     * <li>Then try to inject them.</li>
     * </ol>
     *
     * @param clazz Not used
     * @param testInstance The instance of the test, should not be null.
     *
     * @see org.mockito.plugins.AnnotationEngine#process(Class, Object)
     */
    public void process(Class<?> clazz, Object testInstance)
    {
        processIndependentAnnotations(testInstance.getClass(), testInstance);
        processInjectMocks(testInstance.getClass(), testInstance);
    }

    private void processInjectMocks(final Class<?> clazz, final Object testInstance)
    {
        Class<?> classContext = clazz;
        while (classContext != Object.class)
        {
            injectMocks(testInstance);
            classContext = classContext.getSuperclass();
        }
    }

    private void processIndependentAnnotations(final Class<?> clazz, final Object testInstance)
    {
        Class<?> classContext = clazz;
        while (classContext != Object.class)
        {
            processFluentMockAnnotations(classContext, testInstance);
            //this will create @Mocks, @Captors, etc:
            delegate.process(classContext, testInstance);
            //this will create @Spies:
            spyAnnotationEngine.process(classContext, testInstance);

            classContext = classContext.getSuperclass();
        }
    }

    private void processFluentMockAnnotations(final Class<?> clazz, final Object testInstance)
    {
        final Field[] fields = clazz.getDeclaredFields();
        for (Field field : fields)
        {
            for (Annotation annotation : field.getAnnotations())
            {
                if (annotation instanceof FluentMock)
                {
                    final Object mock = Mockito.mock(field.getType(), withSettings().name(field.getName()).defaultAnswer(new FluentAnswer()));
                    try
                    {
                        setField(testInstance, field, mock);
                    }
                    catch (Exception e)
                    {
                        throw new MockitoException("Problems setting field " + field.getName() + " annotated with " + annotation, e);
                    }

                }
            }
        }
    }


    /**
     * Initializes mock/spies dependencies for objects annotated with
     * &#064;InjectMocks for given testClassInstance.
     * <p>
     * See examples in javadoc for {@link MockitoAnnotations} class.
     *
     * @param testClassInstance
     *            Test class, usually <code>this</code>
     */
    public void injectMocks(final Object testClassInstance)
    {
        Class<?> clazz = testClassInstance.getClass();
        final Set<Field> mockDependentFields = new HashSet<>();
        final Set<Object> mocks = newMockSafeHashSet();

        while (clazz != Object.class)
        {
            new InjectMocksScanner(clazz).addTo(mockDependentFields);
            new MockScanner(testClassInstance, clazz).addPreparedMocks(mocks);
            clazz = clazz.getSuperclass();
        }

        new DefaultInjectionEngine().injectMocksOnFields(mockDependentFields, mocks, testClassInstance);
    }

}
