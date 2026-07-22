/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

/**
 * Verifies, for every concrete class directly extending {@link UnifiedRecordValue} under {@code
 * io.camunda.zeebe.protocol.impl.record.value} (and its subpackages), that the {@code int} literal
 * passed to {@code super(N)} in each constructor matches exactly the number of {@code
 * declareProperty(...)} calls made in that same constructor.
 *
 * <p>{@link UnifiedRecordValue#UnifiedRecordValue(int)} takes the expected number of declared
 * properties as a size hint. If that number and the actual number of {@code declareProperty(...)}
 * calls drift apart, no compiler error results, but the record's internal MsgPack buffers can be
 * mis-sized. This test catches such drift generically, for every current and future record value
 * class, without hardcoding a class list.
 *
 * <p>ArchUnit's {@link JavaClass}/{@link com.tngtech.archunit.core.domain.JavaConstructor} API
 * exposes which methods a constructor calls, but not the constant {@code int} literal pushed onto
 * the stack before a given call. To recover that literal, this test reads the compiled bytecode of
 * each candidate class directly via ASM, inspecting the constructor's instructions for the {@code
 * invokespecial} call to {@code UnifiedRecordValue(int)} and the constant that was pushed
 * immediately before it.
 */
@AnalyzeClasses(
    packages = "io.camunda.zeebe.protocol.impl.record.value..",
    importOptions = ImportOption.DoNotIncludeTests.class)
final class DeclaredPropertiesCountArchTest {

  private static final String DECLARE_PROPERTY_METHOD_NAME = "declareProperty";
  private static final String CONSTRUCTOR_METHOD_NAME = "<init>";
  private static final String UNIFIED_RECORD_VALUE_CONSTRUCTOR_DESCRIPTOR = "(I)V";
  private static final String UNIFIED_RECORD_VALUE_INTERNAL_NAME =
      Type.getInternalName(UnifiedRecordValue.class);

  @ArchTest
  void superConstructorPropertyCountShouldMatchDeclarePropertyCalls(final JavaClasses classes) {
    ArchRuleDefinition.classes()
        .that(directlyExtendUnifiedRecordValue())
        .should(new DeclaredPropertiesCountCondition())
        .check(classes);
  }

  /**
   * Only concrete classes whose <b>direct</b> superclass is {@link UnifiedRecordValue} are
   * candidates: abstract classes and interfaces are excluded since they typically don't declare the
   * property set themselves, and none of the current record value classes have an intermediate
   * UnifiedRecordValue subclass in between.
   */
  private static DescribedPredicate<JavaClass> directlyExtendUnifiedRecordValue() {
    return new DescribedPredicate<>(
        "are concrete classes whose direct superclass is UnifiedRecordValue") {
      @Override
      public boolean test(final JavaClass javaClass) {
        return isConcreteDirectUnifiedRecordValueSubclass(javaClass);
      }
    };
  }

  private static boolean isConcreteDirectUnifiedRecordValueSubclass(final JavaClass javaClass) {
    if (javaClass.isInterface() || javaClass.getModifiers().contains(JavaModifier.ABSTRACT)) {
      return false;
    }

    return javaClass
        .getRawSuperclass()
        .map(superclass -> superclass.isEquivalentTo(UnifiedRecordValue.class))
        .orElse(false);
  }

  private static List<ConstructorInfo> analyzeConstructors(final JavaClass javaClass)
      throws IOException {
    final String resourceName = javaClass.getFullName().replace('.', '/') + ".class";

    // Reading the .class resource via this test's own classloader is enough, since every
    // candidate class already lives on the test classpath (see the zeebe-protocol-impl test
    // dependency in this module's pom.xml). Using javaClass.reflect() instead would load
    // (and statically initialize) each candidate class just to read its bytecode.
    final ClassLoader classLoader = DeclaredPropertiesCountArchTest.class.getClassLoader();

    try (InputStream classFileStream = classLoader.getResourceAsStream(resourceName)) {
      if (classFileStream == null) {
        throw new IOException("Could not locate the compiled class file for " + resourceName);
      }

      final ClassReader classReader = new ClassReader(classFileStream);
      final List<ConstructorInfo> constructorInfos = new ArrayList<>();

      classReader.accept(
          new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(
                final int access,
                final String name,
                final String descriptor,
                final String signature,
                final String[] exceptions) {
              if (!CONSTRUCTOR_METHOD_NAME.equals(name)) {
                return null;
              }
              return new ConstructorAnalyzer(descriptor, constructorInfos);
            }
          },
          ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);

      return constructorInfos;
    }
  }

  /**
   * Verifies, for a single class, that every constructor calling {@code super(N)} does so with
   * {@code N} equal to the number of {@code declareProperty(...)} invocations made in that same
   * constructor.
   */
  private static final class DeclaredPropertiesCountCondition extends ArchCondition<JavaClass> {

    private DeclaredPropertiesCountCondition() {
      super(
          "call super(N) in its constructor(s) with N exactly equal to the number of"
              + " declareProperty(...) invocations made in that constructor");
    }

    @Override
    public void check(final JavaClass javaClass, final ConditionEvents events) {
      final List<ConstructorInfo> constructorInfos;
      try {
        constructorInfos = analyzeConstructors(javaClass);
      } catch (final IOException e) {
        throw new IllegalStateException(
            "Failed to read the compiled bytecode of " + javaClass.getFullName(), e);
      }

      if (constructorInfos.isEmpty()) {
        // Every class directly extending UnifiedRecordValue is expected to call
        // super(<int literal>) from at least one constructor. If none does (e.g. because it
        // only delegates via this(...) with no constructor ever reaching the super call with a
        // resolvable constant), fail loudly instead of silently skipping the class, so this
        // never masks a real record value class.
        events.add(
            SimpleConditionEvent.violated(
                javaClass,
                javaClass.getFullName()
                    + ": extends UnifiedRecordValue directly, but no constructor could be found"
                    + " that calls super(<int literal>) - cannot verify the declared properties"
                    + " count"));
        return;
      }

      for (final ConstructorInfo constructorInfo : constructorInfos) {
        if (constructorInfo.superArgument() != constructorInfo.declarePropertyCalls()) {
          events.add(
              SimpleConditionEvent.violated(
                  javaClass,
                  String.format(
                      "%s: constructor %s calls super(%d) but invokes declareProperty(...) %d"
                          + " time(s) - these two numbers must be equal",
                      javaClass.getFullName(),
                      constructorInfo.descriptor(),
                      constructorInfo.superArgument(),
                      constructorInfo.declarePropertyCalls())));
        }
      }
    }
  }

  /**
   * Visits a single constructor's bytecode instructions, tracking:
   *
   * <ul>
   *   <li>the last integer constant pushed onto the stack (via {@code iconst}/{@code bipush}
   *       /{@code sipush}/{@code ldc}), so that when the {@code invokespecial} call to {@code
   *       UnifiedRecordValue(int)} is seen, we know which literal was passed to it
   *   <li>how many times {@code declareProperty(...)} is invoked in this constructor
   * </ul>
   *
   * <p>Only constructors that directly call {@code UnifiedRecordValue(int)} with a resolvable
   * constant int literal produce a {@link ConstructorInfo}; other constructors (e.g. ones
   * delegating via {@code this(...)}) are ignored, since the invariant is only meaningful at the
   * constructor that actually calls the super constructor.
   */
  private static final class ConstructorAnalyzer extends MethodVisitor {

    private final String descriptor;
    private final List<ConstructorInfo> sink;
    private Integer pendingIntConstant;
    private Integer superArgument;
    private int declarePropertyCalls;

    private ConstructorAnalyzer(final String descriptor, final List<ConstructorInfo> sink) {
      super(Opcodes.ASM9);
      this.descriptor = descriptor;
      this.sink = sink;
    }

    @Override
    public void visitInsn(final int opcode) {
      if (opcode >= Opcodes.ICONST_M1 && opcode <= Opcodes.ICONST_5) {
        pendingIntConstant = opcode - Opcodes.ICONST_0;
      }
    }

    @Override
    public void visitIntInsn(final int opcode, final int operand) {
      if (opcode == Opcodes.BIPUSH || opcode == Opcodes.SIPUSH) {
        pendingIntConstant = operand;
      }
    }

    @Override
    public void visitLdcInsn(final Object value) {
      pendingIntConstant = value instanceof Integer ? (Integer) value : null;
    }

    @Override
    public void visitMethodInsn(
        final int opcode,
        final String owner,
        final String name,
        final String methodDescriptor,
        final boolean isInterface) {
      if (DECLARE_PROPERTY_METHOD_NAME.equals(name)) {
        declarePropertyCalls++;
      } else if (superArgument == null
          && opcode == Opcodes.INVOKESPECIAL
          && CONSTRUCTOR_METHOD_NAME.equals(name)
          && UNIFIED_RECORD_VALUE_INTERNAL_NAME.equals(owner)
          && UNIFIED_RECORD_VALUE_CONSTRUCTOR_DESCRIPTOR.equals(methodDescriptor)) {
        superArgument = pendingIntConstant;
      }
      // A method call changes what's on top of the stack; forget any stale constant.
      pendingIntConstant = null;
    }

    @Override
    public void visitEnd() {
      if (superArgument != null) {
        sink.add(new ConstructorInfo(descriptor, superArgument, declarePropertyCalls));
      }
    }
  }

  private record ConstructorInfo(String descriptor, int superArgument, int declarePropertyCalls) {}
}
