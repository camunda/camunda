/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.security;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.util.Date;

public class SecurityTestUtil {

  public static RSAKey getRsaJWK(final JWSAlgorithm alg) throws JOSEException {
    return new RSAKeyGenerator(2048)
        .keyID("123")
        .keyUse(KeyUse.SIGNATURE)
        .algorithm(alg)
        .generate();
  }

  public static ECKey getEcJWK(final JWSAlgorithm alg, final Curve curve) throws JOSEException {
    return new ECKeyGenerator(curve)
        .algorithm(alg)
        .keyUse(KeyUse.SIGNATURE)
        .keyID("345")
        .generate();
  }

  public static String signAndSerialize(
      final RSAKey rsaKey,
      final JWSAlgorithm alg,
      final JWTClaimsSet claimsSet,
      final JOSEObjectType type)
      throws JOSEException {
    // Create RSA-signer with the private key
    final JWSSigner rsaSigner = new RSASSASigner(rsaKey);

    final SignedJWT rsaSignedJWT =
        new SignedJWT(
            new JWSHeader.Builder(alg).type(type).keyID(rsaKey.getKeyID()).build(), claimsSet);

    rsaSignedJWT.sign(rsaSigner);
    final String serializedJwt = rsaSignedJWT.serialize();
    System.out.println("JWT serialized=" + serializedJwt);
    return serializedJwt;
  }

  public static String signAndSerialize(
      final RSAKey rsaKey, final JWSAlgorithm alg, final JWTClaimsSet claimsSet)
      throws JOSEException {
    return signAndSerialize(rsaKey, alg, claimsSet, JOSEObjectType.JWT);
  }

  public static String signAndSerialize(final RSAKey rsaKey, final JWSAlgorithm alg)
      throws JOSEException {
    return signAndSerialize(rsaKey, alg, getDefaultClaimsSet());
  }

  public static String signAndSerialize(
      final RSAKey rsaKey, final JWSAlgorithm alg, final JOSEObjectType type) throws JOSEException {
    return signAndSerialize(rsaKey, alg, getDefaultClaimsSet(), type);
  }

  public static String signAndSerialize(
      final ECKey ecKey, final JWSAlgorithm alg, final JOSEObjectType type) throws JOSEException {
    // Create EC-signer with the private key
    final ECDSASigner ecSigner = new ECDSASigner(ecKey);

    final SignedJWT ecSignedJWT =
        new SignedJWT(
            new JWSHeader.Builder(alg).type(type).keyID(ecKey.getKeyID()).build(),
            getDefaultClaimsSet());

    ecSignedJWT.sign(ecSigner);
    final String ecSerializedJwt = ecSignedJWT.serialize();
    System.out.println("JWT serialized=" + ecSerializedJwt);
    return ecSerializedJwt;
  }

  public static String signAndSerialize(final ECKey ecKey, final JWSAlgorithm alg)
      throws JOSEException {
    return signAndSerialize(ecKey, alg, JOSEObjectType.JWT);
  }

  public static JWTClaimsSet getDefaultClaimsSet() {
    // prepare default JWT claims set
    return new JWTClaimsSet.Builder()
        .subject("alice")
        .issuer("http://localhost")
        .expirationTime(new Date(new Date().getTime() + 60 * 1000))
        .build();
  }
}
