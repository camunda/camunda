#!/usr/bin/env bash
# Regenerates the throwaway PKI used by ClientCertificateIdentityFilterTest. Test-only keys.
# Usage: ./generate.sh (from this directory). Requires OpenSSL 3.
set -euo pipefail
cd "$(dirname "$0")"
rm -f ./*.crt ./*.key ./*.p12 ./*.csr ./*.srl ./*.pem
DAYS=36500
PASS=changeit

ca() { # name
  openssl req -x509 -newkey ec -pkeyopt ec_paramgen_curve:P-256 -nodes -days "$DAYS" \
    -subj "/CN=$1" -addext "basicConstraints=critical,CA:TRUE" \
    -addext "keyUsage=critical,keyCertSign,cRLSign" -keyout "$1.key" -out "$1.crt" 2>/dev/null
}

leaf() { # name ca cn san(optional)
  local ext; ext=$(mktemp)
  echo "basicConstraints=CA:FALSE" > "$ext"
  if [[ -n "${4:-}" ]]; then echo "subjectAltName=$4" >> "$ext"; fi
  openssl req -newkey ec -pkeyopt ec_paramgen_curve:P-256 -nodes -subj "/CN=$3" \
    -keyout "$1.key" -out "$1.csr" 2>/dev/null
  openssl x509 -req -in "$1.csr" -CA "$2.crt" -CAkey "$2.key" -CAcreateserial -days "$DAYS" \
    -extfile "$ext" -out "$1.crt" 2>/dev/null
  openssl pkcs12 -export -inkey "$1.key" -in "$1.crt" -certfile "$2.crt" -name "$1" \
    -passout "pass:$PASS" -out "$1.p12"
  rm -f "$1.csr" "$ext"
}

ca client-ca
ca rotated-ca
ca untrusted-ca

leaf server client-ca gateway "DNS:localhost,IP:127.0.0.1"
leaf worker client-ca worker "DNS:worker.example.com"
leaf intruder client-ca intruder "DNS:intruder.example.com"
leaf legacy client-ca worker.example.com
leaf rotated rotated-ca worker "DNS:worker.example.com"
leaf untrusted untrusted-ca worker "DNS:worker.example.com"

# what the server trusts for client certificates: both CAs, as during a CA rotation
cat client-ca.crt rotated-ca.crt > trusted-client-cas.pem
rm -f ./*.srl rotated-ca.key untrusted-ca.key client-ca.key \
  worker.key intruder.key legacy.key rotated.key untrusted.key
