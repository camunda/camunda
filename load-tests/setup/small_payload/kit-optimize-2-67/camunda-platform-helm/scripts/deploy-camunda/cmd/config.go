package cmd

import (
	"fmt"
	"os"
	"scripts/deploy-camunda/config"
	"scripts/deploy-camunda/format"
	"strings"

	"github.com/spf13/cobra"
)

// newConfigCommand creates the config subcommand.
func newConfigCommand() *cobra.Command {
	configCmd := &cobra.Command{
		Use:   "config",
		Short: "Manage deploy-camunda configuration and active deployment",
	}

	configCmd.AddCommand(newListCommand())
	configCmd.AddCommand(newShowCommand())
	configCmd.AddCommand(newUseCommand())

	return configCmd
}

// newListCommand creates the list subcommand.
func newListCommand() *cobra.Command {
	return &cobra.Command{
		Use:   "list",
		Short: "List configured deployments",
		RunE: func(cmd *cobra.Command, args []string) error {
			cfgPath, err := config.ResolvePath(configFile)
			if err != nil {
				return err
			}
			rc, err := config.Read(cfgPath, false)
			if err != nil {
				return err
			}
			active := rc.Current
			for name := range rc.Deployments {
				marker := " "
				if name == active {
					marker = "*"
				}
				fmt.Fprintf(os.Stdout, "%s %s\n", marker, name)
			}
			if len(rc.Deployments) == 0 {
				fmt.Fprintln(os.Stdout, "(no deployments configured)")
			}
			return nil
		},
	}
}

// newShowCommand creates the show subcommand.
func newShowCommand() *cobra.Command {
	return &cobra.Command{
		Use:               "show [name|current]",
		Short:             "Show a deployment (merged with defaults)",
		Args:              cobra.RangeArgs(0, 1),
		ValidArgsFunction: completeDeploymentNames,
		RunE: func(cmd *cobra.Command, args []string) error {
			cfgPath, err := config.ResolvePath(configFile)
			if err != nil {
				return err
			}
			rc, err := config.Read(cfgPath, false)
			if err != nil {
				return err
			}
			name := ""
			if len(args) == 0 || args[0] == "current" {
				name = rc.Current
			} else {
				name = args[0]
			}
			if strings.TrimSpace(name) == "" {
				return fmt.Errorf("no deployment selected; set a current one with: deploy-camunda config use <name>")
			}
			dep, ok := rc.Deployments[name]
			if !ok {
				return fmt.Errorf("deployment %q not found", name)
			}

			return format.PrintDeploymentConfig(name, dep, *rc)
		},
	}
}

// newUseCommand creates the use subcommand.
func newUseCommand() *cobra.Command {
	return &cobra.Command{
		Use:               "use <name>",
		Short:             "Set the active deployment",
		Args:              cobra.ExactArgs(1),
		ValidArgsFunction: completeDeploymentNames,
		RunE: func(cmd *cobra.Command, args []string) error {
			name := args[0]
			cfgPath, err := config.ResolvePath(configFile)
			if err != nil {
				return err
			}
			rc, err := config.Read(cfgPath, false)
			if err != nil {
				return err
			}
			if rc.Deployments == nil {
				return fmt.Errorf("no deployments configured in %q", cfgPath)
			}
			if _, ok := rc.Deployments[name]; !ok {
				return fmt.Errorf("deployment %q not found in %q", name, cfgPath)
			}
			if err := config.WriteCurrentOnly(cfgPath, name); err != nil {
				return err
			}
			fmt.Fprintf(os.Stdout, "Now using deployment %q in %s\n", name, cfgPath)
			return nil
		},
	}
}

// completeDeploymentNames provides shell completion for deployment names.
func completeDeploymentNames(cmd *cobra.Command, args []string, toComplete string) ([]string, cobra.ShellCompDirective) {
	cfgPath, err := config.ResolvePath(configFile)
	if err != nil {
		return nil, cobra.ShellCompDirectiveDefault
	}
	rc, err := config.Read(cfgPath, false)
	if err != nil {
		return nil, cobra.ShellCompDirectiveDefault
	}
	var names []string
	for name := range rc.Deployments {
		if toComplete == "" || strings.HasPrefix(name, toComplete) {
			names = append(names, name)
		}
	}
	return names, cobra.ShellCompDirectiveNoFileComp
}
