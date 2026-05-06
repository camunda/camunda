#!/usr/bin/env bash
# gen-certs.sh — generates self-signed TLS certificates for the SASL/SCRAM and mTLS examples.
#
# Produces:
#   certs/ca.crt              — shared CA certificate
#   certs/broker.p12          — broker keystore (PKCS12, password: changeit)
#   certs/truststore.p12      — truststore containing the CA cert (password: changeit)
#   certs/client-zeebe.p12    — Zeebe client keystore for mTLS (PKCS12, password: changeit)
#
# Requirements: openssl, keytool (JDK)
#
# Usage:
#   bash zeebe/exporters/camunda-kafka-exporter/examples/gen-certs.sh
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CERTS_DIR="${SCRIPT_DIR}/certs"
PASS="changeit"

command -v openssl >/dev/null 2>&1 || { echo "openssl is required"; exit 1; }
command -v keytool  >/dev/null 2>&1 || { echo "keytool (JDK) is required"; exit 1; }

mkdir -p "${CERTS_DIR}"

echo "Generating CA..."
openssl genrsa -out "${CERTS_DIR}/ca.key" 4096 2>/dev/null
openssl req -new -x509 -days 3650 \
  -key  "${CERTS_DIR}/ca.key" \
  -out  "${CERTS_DIR}/ca.crt" \
  -subj "/CN=CamundaKafkaCA/O=Camunda/C=DE"

echo "Generating broker certificate..."
openssl genrsa -out "${CERTS_DIR}/broker.key" 2048 2>/dev/null
openssl req -new \
  -key  "${CERTS_DIR}/broker.key" \
  -out  "${CERTS_DIR}/broker.csr" \
  -subj "/CN=kafka/O=Camunda/C=DE"

# SAN must include the Docker service name "kafka" and localhost for external access.
cat > "${CERTS_DIR}/broker-ext.cnf" << 'EOF'
[v3_req]
subjectAltName = DNS:kafka,DNS:localhost,IP:127.0.0.1
EOF

openssl x509 -req -days 365 \
  -in      "${CERTS_DIR}/broker.csr" \
  -CA      "${CERTS_DIR}/ca.crt" \
  -CAkey   "${CERTS_DIR}/ca.key" \
  -CAcreateserial \
  -extfile "${CERTS_DIR}/broker-ext.cnf" \
  -extensions v3_req \
  -out     "${CERTS_DIR}/broker.crt" 2>/dev/null

openssl pkcs12 -export \
  -passout "pass:${PASS}" \
  -in   "${CERTS_DIR}/broker.crt" \
  -inkey "${CERTS_DIR}/broker.key" \
  -name  broker \
  -out  "${CERTS_DIR}/broker.p12"

echo "Generating truststore (CA cert)..."
# Remove existing truststore so keytool -importcert does not prompt.
rm -f "${CERTS_DIR}/truststore.p12"
keytool -noprompt -importcert \
  -alias      ca \
  -file       "${CERTS_DIR}/ca.crt" \
  -keystore   "${CERTS_DIR}/truststore.p12" \
  -storetype  PKCS12 \
  -storepass  "${PASS}"

echo "Generating Zeebe client certificate (for mTLS)..."
openssl genrsa -out "${CERTS_DIR}/client-zeebe.key" 2048 2>/dev/null
openssl req -new \
  -key  "${CERTS_DIR}/client-zeebe.key" \
  -out  "${CERTS_DIR}/client-zeebe.csr" \
  -subj "/CN=zeebe/O=Camunda/C=DE"

openssl x509 -req -days 365 \
  -in      "${CERTS_DIR}/client-zeebe.csr" \
  -CA      "${CERTS_DIR}/ca.crt" \
  -CAkey   "${CERTS_DIR}/ca.key" \
  -CAcreateserial \
  -out     "${CERTS_DIR}/client-zeebe.crt" 2>/dev/null

openssl pkcs12 -export \
  -passout "pass:${PASS}" \
  -in    "${CERTS_DIR}/client-zeebe.crt" \
  -inkey "${CERTS_DIR}/client-zeebe.key" \
  -name  zeebe \
  -out   "${CERTS_DIR}/client-zeebe.p12"

# Clean up intermediate files.
rm -f "${CERTS_DIR}"/*.csr "${CERTS_DIR}"/*.srl "${CERTS_DIR}/broker-ext.cnf"

echo ""
echo "Certificates written to ${CERTS_DIR}:"
ls -1 "${CERTS_DIR}"
echo ""
echo "All passwords: ${PASS}"
echo "Done."
