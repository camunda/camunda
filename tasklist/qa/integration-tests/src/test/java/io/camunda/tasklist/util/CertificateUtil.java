/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE (“USE”), YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
 * “Licensee” means you, an individual, or the entity on whose behalf you receive the Software.
 *
 * Permission is hereby granted, free of charge, to the Licensee obtaining a copy of this Software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject in each case to the following conditions:
 * Condition 1: If the Licensee distributes the Software or any derivative works of the Software, the Licensee must attach this Agreement.
 * Condition 2: Without limiting other conditions in this Agreement, the grant of rights is solely for non-production use as defined below.
 * "Non-production use" means any use of the Software that is not directly related to creating products, services, or systems that generate revenue or other direct or indirect economic benefits.  Examples of permitted non-production use include personal use, educational use, research, and development. Examples of prohibited production use include, without limitation, use for commercial, for-profit, or publicly accessible systems or use for commercial or revenue-generating purposes.
 *
 * If the Licensee is in breach of the Conditions, this Agreement, including the rights granted under it, will automatically terminate with immediate effect.
 *
 * SUBJECT AS SET OUT BELOW, THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * NOTHING IN THIS AGREEMENT EXCLUDES OR RESTRICTS A PARTY’S LIABILITY FOR (A) DEATH OR PERSONAL INJURY CAUSED BY THAT PARTY’S NEGLIGENCE, (B) FRAUD, OR (C) ANY OTHER LIABILITY TO THE EXTENT THAT IT CANNOT BE LAWFULLY EXCLUDED OR RESTRICTED.
 */
package io.camunda.tasklist.util;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

public final class CertificateUtil {

  private static final String ALGORITHM = "RSA";
  private static final int CERTIFICATE_KEY_SIZE = 2048;

  private CertificateUtil() {}

  public static void generateRSACertificate(final File certFile, final File privateKeyFile)
      throws Exception {
    final KeyPair keyPair = generateKeyPair();
    final X509Certificate cert =
        generateSelfSignedCertificate(keyPair, "CN=localhost", "SHA256withRSA");
    saveCertificateToFile(cert, certFile);
    savePrivateKeyToFile(keyPair.getPrivate(), privateKeyFile);
  }

  private static KeyPair generateKeyPair() throws NoSuchAlgorithmException {
    final KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance(ALGORITHM);
    keyPairGenerator.initialize(CERTIFICATE_KEY_SIZE);
    return keyPairGenerator.generateKeyPair();
  }

  private static void saveCertificateToFile(final X509Certificate cert, final File certFile)
      throws CertificateEncodingException, IOException {
    try (final Writer writer = new FileWriter(certFile);
        final JcaPEMWriter pemWriter = new JcaPEMWriter(writer)) {
      pemWriter.writeObject(cert);
    }
  }

  private static void savePrivateKeyToFile(final PrivateKey privateKey, final File privateKeyFile)
      throws IOException {
    try (final Writer writer = new FileWriter(privateKeyFile);
        final JcaPEMWriter pemWriter = new JcaPEMWriter(writer)) {
      pemWriter.writeObject(privateKey);
    }
  }

  private static X509Certificate generateSelfSignedCertificate(
      final KeyPair keyPair, final String dn, final String signatureAlgorithm) throws Exception {
    final X500Name subjectName = new X500Name(dn);
    final X500Name issuerName = subjectName; // Self-signed

    final BigInteger serialNumber = BigInteger.valueOf(System.currentTimeMillis());
    final Instant now = Instant.now();
    final Date startDate = Date.from(now);
    final Date endDate = Date.from(now.plus(Duration.ofDays(365)));

    final JcaX509v3CertificateBuilder certBuilder =
        new JcaX509v3CertificateBuilder(
            issuerName, serialNumber, startDate, endDate, subjectName, keyPair.getPublic());

    final ContentSigner signer =
        new JcaContentSignerBuilder(signatureAlgorithm).build(keyPair.getPrivate());

    final X509CertificateHolder certHolder = certBuilder.build(signer);
    return new JcaX509CertificateConverter().getCertificate(certHolder);
  }
}
