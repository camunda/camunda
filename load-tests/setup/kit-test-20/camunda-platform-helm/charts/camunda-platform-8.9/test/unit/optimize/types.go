package optimize

type OptimizeConfigYAML struct {
	Container ContainerYAML `yaml:"container"`
	Zeebe     ZeebeYAML     `yaml:"zeebe"`
}

type ContainerYAML struct {
	ContextPath string `yaml:"contextPath"`
}

type ZeebeYAML struct {
	Name string `yaml:"name"`
}
