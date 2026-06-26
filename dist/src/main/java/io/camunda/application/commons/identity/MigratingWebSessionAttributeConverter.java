/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.identity;

import io.camunda.security.api.model.CamundaAuthentication;
import io.camunda.security.spring.session.WebSessionAttributeConverter;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectStreamClass;
import java.util.Map;
import org.springframework.core.convert.TypeDescriptor;
import org.springframework.core.convert.support.GenericConversionService;
import org.springframework.core.serializer.Deserializer;
import org.springframework.core.serializer.support.DeserializingConverter;
import org.springframework.core.serializer.support.SerializingConverter;

/**
 * {@link WebSessionAttributeConverter} that handles deserialization of session attributes
 * serialized by older versions of the application, where class FQNs have since changed.
 *
 * <p>Specifically, {@code CamundaAuthentication} moved from the monorepo ({@code
 * io.camunda.security.auth}) to CSL ({@code io.camunda.security.api.model}). Sessions persisted
 * before this migration contain the old FQN in their serialized byte stream. The {@link
 * MigratingObjectInputStream} remaps these to the current class at read time, allowing existing
 * sessions to survive the upgrade without forcing users to re-authenticate.
 */
final class MigratingWebSessionAttributeConverter implements WebSessionAttributeConverter {

  // Old FQN baked into sessions serialized before the CSL migration.
  // Safe to remove once all sessions from pre-migration deployments have naturally expired
  // (bounded by the configured maxInactiveInterval). When removed, also delete:
  //   - MigratingDeserializer, MigratingObjectInputStream (this file)
  //   - dist/src/test/java/io/camunda/security/auth/CamundaAuthentication.java (test stub)
  //   - the old-FQN test case in MigratingWebSessionAttributeConverterTest
  private static final Map<String, Class<?>> CLASS_RENAMES =
      Map.of("io.camunda.security.auth.CamundaAuthentication", CamundaAuthentication.class);

  private final GenericConversionService conversionService;

  MigratingWebSessionAttributeConverter() {
    conversionService = new GenericConversionService();
    conversionService.addConverter(Object.class, byte[].class, new SerializingConverter());
    conversionService.addConverter(
        byte[].class, Object.class, new DeserializingConverter(new MigratingDeserializer()));
  }

  @Override
  public Object deserialize(final byte[] value) {
    return conversionService.convert(
        value, TypeDescriptor.valueOf(byte[].class), TypeDescriptor.valueOf(Object.class));
  }

  @Override
  public byte[] serialize(final Object value) {
    return (byte[])
        conversionService.convert(
            value, TypeDescriptor.valueOf(Object.class), TypeDescriptor.valueOf(byte[].class));
  }

  private static final class MigratingDeserializer implements Deserializer<Object> {

    @Override
    public Object deserialize(final InputStream inputStream) throws IOException {
      try (final var ois = new MigratingObjectInputStream(inputStream)) {
        return ois.readObject();
      } catch (final ClassNotFoundException e) {
        throw new IOException("Failed to deserialize object type", e);
      }
    }
  }

  private static final class MigratingObjectInputStream extends ObjectInputStream {

    MigratingObjectInputStream(final InputStream in) throws IOException {
      super(in);
    }

    @Override
    protected Class<?> resolveClass(final ObjectStreamClass desc)
        throws IOException, ClassNotFoundException {
      final Class<?> remapped = CLASS_RENAMES.get(desc.getName());
      return remapped != null ? remapped : super.resolveClass(desc);
    }
  }
}
