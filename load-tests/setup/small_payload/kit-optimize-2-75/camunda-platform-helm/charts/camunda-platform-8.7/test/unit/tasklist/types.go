package tasklist

type TasklistConfigYAML struct {
	Server          ServerYAML          `yaml:"server"`
	Spring          SpringYAML          `yaml:"spring"`
	Camunda         CamundaYAML         `yaml:"camunda"`
	Security        SecurityYAML        `yaml:"security"`
	CamundaTasklist CamundaTasklistYAML `yaml:"camunda.tasklist"`
}

type ServerYAML struct {
	Servlet ServletYAML `yaml:"servlet"`
}

type ServletYAML struct {
	ContextPath string `yaml:"contextPath"`
}

type SpringYAML struct {
	Profiles ProfilesYAML `yaml:"profiles"`
}

type ProfilesYAML struct {
	Active string `yaml:"active"`
}

type CamundaYAML struct {
	Identity IdentityYAML `yaml:"identity"`
}

type IdentityYAML struct {
	ClientId string `yaml:"clientId"`
}

type SecurityYAML struct {
	OAuth2 OAuth2YAML `yaml:"oauth2"`
}

type OAuth2YAML struct {
	ResourceServer ResourceServerYAML `yaml:"resourceserver"`
}

type ResourceServerYAML struct {
	JWT JWTYAML `yaml:"jwt"`
}

type JWTYAML struct {
	IssuerURI string `yaml:"issuer-uri"`
}

type CamundaTasklistYAML struct {
	Identity     TasklistIdentityYAML `yaml:"identity"`
	MultiTenancy MultiTenancyYAML     `yaml:"multiTenancy"`
}
type TasklistIdentityYAML struct {
	RedirectRootURL string `yaml:"redirectRootUrl"`
}

type MultiTenancyYAML struct {
	Enabled string `yaml:"enabled"`
}
