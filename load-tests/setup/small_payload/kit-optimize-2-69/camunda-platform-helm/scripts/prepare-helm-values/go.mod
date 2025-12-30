module scripts/prepare-helm-values

go 1.25.0

require (
	github.com/joho/godotenv v1.5.1
	github.com/spf13/cobra v1.10.2
	gopkg.in/yaml.v3 v3.0.1
	scripts/camunda-core v0.0.0
)

require (
	github.com/inconshreveable/mousetrap v1.1.0 // indirect
	github.com/jwalton/gchalk v1.3.0 // indirect
	github.com/jwalton/go-supportscolor v1.1.0 // indirect
	github.com/mattn/go-colorable v0.1.13 // indirect
	github.com/mattn/go-isatty v0.0.19 // indirect
	github.com/rs/zerolog v1.34.0 // indirect
	github.com/spf13/pflag v1.0.9 // indirect
	golang.org/x/sys v0.38.0 // indirect
	golang.org/x/term v0.37.0 // indirect
)

replace scripts/camunda-core => ../camunda-core
