package io.camunda.optimize;

import java.lang.annotation.Annotation;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldNameConstants;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.reflections.Reflections;
import org.reflections.scanners.Scanners;

public class CodeQualityTest {
  @Test
  public void testNoClassesWithAnnotation() {
    Reflections reflections = new Reflections("io.camunda.optimize", Scanners.TypesAnnotated);
    Set<Class<?>> allClasses = reflections.getSubTypesOf(Object.class);

    Class<? extends Annotation>[] forbiddenAnnotations = new Class[] {
        NoArgsConstructor.class,
        AllArgsConstructor.class,
        RequiredArgsConstructor.class,
        FieldNameConstants.class
    };

    for (Class<?> clazz : allClasses) {
      for (Class<? extends Annotation> forbiddenAnnotation : forbiddenAnnotations) {
        if (clazz.isAnnotationPresent(forbiddenAnnotation)) {
          Assertions.fail("Class " + clazz.getName() + " is annotated with forbidden annotation @" + forbiddenAnnotation.getSimpleName());
        }
      }
    }
  }
}