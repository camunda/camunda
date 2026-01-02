package identity

type IdentityConfigYAML struct {
	Identity IdentityYAML `yaml:"identity"`
	Server   ServerYAML   `yaml:"server"`
	Spring   SpringYAML   `yaml:"spring"`
	Camunda  CamundaYAML  `yaml:"camunda"`
}

type IdentityYAML struct {
	Url          string           `yaml:"url"`
	Flags        FlagsYAML        `yaml:"flags"`
	AuthProvider AuthProviderYAML `yaml:"authProvider"`
}

type AuthProviderYAML struct {
	BackendUrl string `yaml:"backend-url"`
}

type ServerYAML struct {
	Servlet ServletYAML `yaml:"servlet"`
}

type ServletYAML struct {
	ContextPath string `yaml:"context-path"`
}

type FlagsYAML struct {
	MultiTenancy string `yaml:"multi-tenancy"`
}

type SpringYAML struct {
	DataSource DataSourceYAML `yaml:"datasource"`
}

type DataSourceYAML struct {
	Url      string `yaml:"url"`
	Username string `yaml:"username"`
}

type CamundaYAML struct {
	Identity CamundaIdentityYAML `yaml:"identity"`
}

type CamundaIdentityYAML struct {
	Audience         string `yaml:"audience"`
	ClientId         string `yaml:"client-id"`
	BaseUrl          string `yaml:"baseUrl"`
	Issuer           string `yaml:"issuer"`
	IssuerBackendUrl string `yaml:"issuerBackendUrl"`
}
