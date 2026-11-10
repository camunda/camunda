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
import java.io.ObjectInputFilter;
import java.io.ObjectInputStream;
import java.io.ObjectStreamClass;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.convert.TypeDescriptor;
import org.springframework.core.convert.support.GenericConversionService;
import org.springframework.core.serializer.Deserializer;
import org.springframework.core.serializer.support.DeserializingConverter;
import org.springframework.core.serializer.support.SerializingConverter;

/**
 * Deserializes session attributes persisted before the {@code io.camunda.security.auth ->
 * io.camunda.security.api.model} CamundaAuthentication class migration.
 */
final class MigratingWebSessionAttributeConverter implements WebSessionAttributeConverter {

  private static final Logger LOG =
      LoggerFactory.getLogger(MigratingWebSessionAttributeConverter.class);

  // Pre-migration FQN -> current class; remove together with MigratingDeserializer,
  // MigratingObjectInputStream, and the test stub once old sessions have naturally expired.
  private static final Map<String, Class<?>> CLASS_RENAMES =
      Map.of("io.camunda.security.auth.CamundaAuthentication", CamundaAuthentication.class);

  /**
   * Classes a persisted session attribute may contain, deny by default. Session attributes are read
   * back from secondary storage, which must not be able to make the process instantiate arbitrary
   * classes. The list was taken from the sessions created by the basic-auth and OIDC acceptance
   * tests. The JDK and JSON entries are the value types of the authentication claims, the Spring
   * Security entries the OIDC login. Array types are checked by their component type.
   */
  private static final List<String> ALLOWED_CLASSES =
      List.of(
          "io.camunda.security.api.model.CamundaAuthentication",
          // basic login
          "org.springframework.security.authentication.UsernamePasswordAuthenticationToken",
          "org.springframework.security.core.userdetails.User",
          "org.springframework.security.core.userdetails.User$AuthorityComparator",
          // OIDC login
          "org.springframework.security.core.context.SecurityContextImpl",
          "org.springframework.security.core.authority.*",
          "org.springframework.security.authentication.AbstractAuthenticationToken",
          "org.springframework.security.web.authentication.WebAuthenticationDetails",
          "org.springframework.security.oauth2.client.OAuth2AuthorizedClient",
          "org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken",
          "org.springframework.security.oauth2.client.registration.ClientRegistration",
          "org.springframework.security.oauth2.client.registration.ClientRegistration$ClientSettings",
          "org.springframework.security.oauth2.client.registration.ClientRegistration$ProviderDetails",
          "org.springframework.security.oauth2.client.registration.ClientRegistration$ProviderDetails$UserInfoEndpoint",
          "org.springframework.security.oauth2.core.**",
          // claim values
          "net.minidev.json.JSONObject",
          "net.minidev.json.JSONArray",
          "com.nimbusds.oauth2.sdk.util.OrderedJSONObject",
          "java.lang.Object",
          "java.lang.Number",
          "java.lang.Boolean",
          "java.lang.Long",
          "java.net.URL",
          "java.time.Instant",
          "java.time.Ser",
          "java.util.ArrayList",
          "java.util.HashMap",
          "java.util.HashSet",
          "java.util.LinkedHashMap",
          "java.util.LinkedHashSet",
          "java.util.TreeSet",
          "java.util.CollSer",
          // the JDK picks the nested class by size (List12, ListN, ...); the trailing * is a prefix
          // match, "$**" would match nothing
          "java.util.ImmutableCollections$*",
          "java.util.Collections$EmptyList",
          "java.util.Collections$EmptyMap",
          "java.util.Collections$EmptySet",
          "java.util.Collections$SingletonList",
          "java.util.Collections$SingletonMap",
          "java.util.Collections$SingletonSet",
          "java.util.Map$Entry",
          "java.util.Collections$UnmodifiableCollection",
          "java.util.Collections$UnmodifiableList",
          "java.util.Collections$UnmodifiableMap",
          "java.util.Collections$UnmodifiableRandomAccessList",
          "java.util.Collections$UnmodifiableSet");

  private static final ObjectInputFilter ALLOWLIST =
      ObjectInputFilter.Config.createFilter(
          "maxdepth=50;maxrefs=100000;maxarray=100000;"
              + String.join(";", ALLOWED_CLASSES)
              + ";!*");

  // The JDK exception does not name the rejected class; the log is how an operator finds out which
  // class a session attribute needs added to the allowlist.
  private static final ObjectInputFilter FILTER =
      info -> {
        final var status = ALLOWLIST.checkInput(info);
        if (status == ObjectInputFilter.Status.REJECTED) {
          LOG.warn(
              "Rejected deserialization of a session attribute: class={}, depth={}, references={},"
                  + " arrayLength={}",
              info.serialClass(),
              info.depth(),
              info.references(),
              info.arrayLength());
        }
        return status;
      };

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
      setObjectInputFilter(FILTER);
    }

    @Override
    protected Class<?> resolveClass(final ObjectStreamClass desc)
        throws IOException, ClassNotFoundException {
      final Class<?> remapped = CLASS_RENAMES.get(desc.getName());
      return remapped != null ? remapped : super.resolveClass(desc);
    }
  }
}
