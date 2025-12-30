package kube

import "time"

type ApplyManifestOptions struct {
	Retry bool
	RetryAttempts int
	Timeout time.Duration
}

func DefaultApplyManifestOptions() ApplyManifestOptions {
	return ApplyManifestOptions{
		Retry:         true,
		RetryAttempts: 5,
		Timeout:       30 * time.Second,
	}
}

type ExternalSecretsOptions struct {
	SkipIfCRDMissing bool
	Timeout time.Duration
}

func DefaultExternalSecretsOptions() ExternalSecretsOptions {
	return ExternalSecretsOptions{
		SkipIfCRDMissing: true,
		Timeout:          300 * time.Second,
	}
}

type SecretOptions struct {
	RetryOnConflict bool
	FailIfExists bool
}

func DefaultSecretOptions() SecretOptions {
	return SecretOptions{
		RetryOnConflict: true,
		FailIfExists:    false,
	}
}

type NamespaceOptions struct {
	Labels map[string]string
	Annotations map[string]string
	RetryOnConflict bool
}

func DefaultNamespaceOptions() NamespaceOptions {
	return NamespaceOptions{
		Labels:          make(map[string]string),
		Annotations:     make(map[string]string),
		RetryOnConflict: true,
	}
}

