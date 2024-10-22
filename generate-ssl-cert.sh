#!/bin/bash

# Shared variables
PW="password"

# CA variables
CA_COUNTRY="DE"
CA_STATE="BW"
CA_LOCALITY="Karlsruhe"
CA_ORG_NAME="CA Provider GmbH"
CA_ORG_UNIT="CA responsible"
# IDP (Keycloak) variables
IDP_COUNTRY="DE"
IDP_STATE="BY"
IDP_LOCALITY="Muenchen"
IDP_ORG_NAME="IDP Security Provider Inc"
IDP_ORG_UNIT="Keycloak Dept"
IDP_SERVER_CN="keycloak"
# Identity client variables
USER_COUNTRY="DE"
USER_STATE="BY"
USER_LOCALITY="Muenchen"
USER_ORG_NAME="Camunda"
USER_ORG_UNIT="Identity"
USER_SERVER_CN="identity"
USER_EMAIL_ADDRESS="identity@camunda.com"

function generate_root_ca() {
    # Generate new CA key and certificate
    openssl req -x509 -sha256 -days 3650 -newkey rsa:4096 -keyout rootCA.key -out rootCA.crt -subj "/C=$CA_COUNTRY/ST=$CA_STATE/L=$CA_LOCALITY/O=$CA_ORG_NAME/OU=$CA_ORG_UNIT/CN=Root CA" -passout pass:$PW
}

function generate_keycloak_cert() {
    # Generate new Keycloak key and certificate
    openssl req -new -newkey rsa:4096 -keyout keycloak.key -out keycloak.csr -nodes -subj "/C=$IDP_COUNTRY/ST=$IDP_STATE/L=$IDP_LOCALITY/O=$IDP_ORG_NAME/OU=$IDP_ORG_UNIT/CN=$IDP_SERVER_CN" -passout pass:$PW
    # Define extension params for Keycloak
    cat <<EOF >keycloak.ext
authorityKeyIdentifier=keyid,issuer
basicConstraints=CA:FALSE
subjectAltName = @alt_names

[alt_names]
DNS.1 = $IDP_SERVER_CN
DNS.2 = localhost
EOF
    # Sign keycloak key with CA certificate
    openssl x509 -req -CA rootCA.crt -CAkey rootCA.key -in keycloak.csr -out keycloak.crt -days 365 -CAcreateserial -extfile keycloak.ext -passin pass:$PW
    # Convert keycloak certificate to PEM format
    openssl x509 -in keycloak.crt -out keycloak-crt.pem -outform PEM
    # Convert keycloak key to PEM format
    openssl rsa -in keycloak.key -out keycloak-key.pem
}

function generate_truestore() {
    # Create truststore
    keytool -import -alias root.ca -file rootCA.crt -keypass $PW -keystore truststore.jks -storepass $PW -noprompt
}

function generate_identity_cert() {
    # Create user certificate
    openssl req -new -newkey rsa:4096 -nodes -keyout identity.key -out identity.csr -subj "/emailAddress="$USER_EMAIL_ADDRESS"/C=$USER_COUNTRY/ST=$USER_STATE/L=$USER_LOCALITY/O=$USER_ORG_NAME/OU=$USER_ORG_UNIT/CN=$USER_SERVER_CN"
    # Sign user certificate with CA
    openssl x509 -req -CA rootCA.crt -CAkey rootCA.key -in identity.csr -out identity.crt -days 365 -CAcreateserial -passin pass:$PW
    # Export user certificate
    openssl pkcs12 -export -out identity.p12 -name "identity" -inkey identity.key -in identity.crt -passout pass:$PW
    # Convert identity certificate to PEM format
    openssl x509 -in identity.crt -out identity-crt.pem -outform PEM
    # Convert identity key to PEM format
    openssl rsa -in identity.key -out identity-key.pem
}

# generate_root_ca
# generate_keycloak_cert
# generate_identity_cert
# generate_truestore
