module vault-secret-mapper

go 1.25.0

require (
	gopkg.in/yaml.v3 v3.0.1
	scripts/camunda-core v0.0.0
)

require (
	github.com/jwalton/gchalk v1.3.0 // indirect
	github.com/jwalton/go-supportscolor v1.1.0 // indirect
	github.com/mattn/go-colorable v0.1.13 // indirect
	github.com/mattn/go-isatty v0.0.19 // indirect
	github.com/rs/zerolog v1.34.0 // indirect
	golang.org/x/sys v0.38.0 // indirect
	golang.org/x/term v0.37.0 // indirect
)

replace scripts/camunda-core => ../camunda-core
