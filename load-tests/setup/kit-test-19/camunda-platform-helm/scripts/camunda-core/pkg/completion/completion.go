package completion

import (
	"scripts/camunda-core/pkg/scenarios"

	"github.com/spf13/cobra"
)

// RegisterScenarioCompletion adds tab completion for the scenario flag.
// It expects the command to have a flag for chart path (e.g., "chart" or "chart-path").
func RegisterScenarioCompletion(cmd *cobra.Command, flagName string, scenarioDirFlagName string) {
	_ = cmd.RegisterFlagCompletionFunc(flagName, func(cmd *cobra.Command, args []string, toComplete string) ([]string, cobra.ShellCompDirective) {
		scenarioDir, _ := cmd.Flags().GetString(scenarioDirFlagName)
		if scenarioDir == "" {
			return cobra.AppendActiveHelp(nil, "Please specify --"+scenarioDirFlagName+" first to resolve scenarios"), cobra.ShellCompDirectiveNoFileComp
		}

		list, err := scenarios.List(scenarioDir)
		if err != nil {
			return nil, cobra.ShellCompDirectiveError
		}

		return list, cobra.ShellCompDirectiveNoFileComp
	})
}
