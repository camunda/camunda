package operate

type OperateConfigYAML struct {
	Server         ServerYAML         `yaml:"server"`
	Spring         SpringYAML         `yaml:"spring"`
	CamundaOperate CamundaOperateYAML `yaml:"camunda.operate"`
}

type ServerYAML struct {
	Servlet ServletYAML `yaml:"servlet"`
}

type ServletYAML struct {
	ContextPath string `yaml:"context-path"`
}
type SpringYAML struct {
	Profiles ProfilesYAML `yaml:"profiles"`
}

type ProfilesYAML struct {
	Active string `yaml:"active"`
}

type CamundaOperateYAML struct {
	MultiTenancy MultiTenancyYAML `yaml:"multiTenancy"`
	Identity     IdentityYAML     `yaml:"identity"`
}

type IdentityYAML struct {
	RedirectRootUrl string `yaml:"redirectRootUrl"`
}

type MultiTenancyYAML struct {
	Enabled string `yaml:"enabled"`
}
