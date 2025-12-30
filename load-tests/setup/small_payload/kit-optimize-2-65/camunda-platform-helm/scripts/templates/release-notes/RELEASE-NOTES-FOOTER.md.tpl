### Release Info

{{ getenv "VERSION_MATRIX_RELEASE_INFO" }}

### Verification

For quick verification of the Helm chart integrity using [Cosign](https://docs.sigstore.dev/signing/quickstart/):

```shell
cosign verify-blob {{ getenv "CHART_RELEASE_NAME" }}.tgz \
  --bundle "{{ getenv "CHART_RELEASE_NAME" }}-cosign-bundle.json" \
  --certificate-identity-regex "https://github.com/{{ getenv "GITHUB_REPOSITORY" }}" \
  --certificate-oidc-issuer "https://token.actions.githubusercontent.com"
```

For detailed verification instructions, check the steps in the `{{ getenv "CHART_RELEASE_NAME" }}-cosign-verify.sh` file.
