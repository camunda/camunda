import {Page, Locator, expect} from '@playwright/test';

class ClusterPage {
  private page: Page;
  readonly createNewClusterButton: Locator;
  readonly clusterNameInput: Locator;
  readonly createClusterButton: Locator;
  readonly clusterCreatingStatusText: Locator;
  readonly clusterHealthyStatusText: Locator;
  readonly clusterHealthyIndicatorSelector: string;
  readonly clusterGenerationCompleteMessage: Locator;
  readonly clustersBreadcrumb: Locator;
  readonly deleteClusterButton: Locator;
  readonly confirmDeleteInput: Locator;
  readonly dangerDeleteButton: Locator;
  readonly testClusterLink: Locator;
  readonly connectorSecretsTab: Locator;
  readonly createAClusterButton: Locator;
  readonly clusterVersionOption: Locator;
  readonly replicatedPerformanceCluster: Locator;
  readonly alphaTab: Locator;
  readonly devTab: Locator;
  readonly stableTab: Locator;
  readonly qaGenerationsTab: Locator;
  readonly gcpRegion: Locator;

  constructor(page: Page) {
    this.page = page;
    this.createNewClusterButton = page.getByRole('button', {
      name: 'Create new Cluster',
    });
    this.clusterNameInput = page.getByPlaceholder(
      'Enter a name for the cluster',
    );
    this.createClusterButton = page.getByRole('button', {
      name: 'Create cluster',
    });
    this.clusterCreatingStatusText = page.getByText('Creating', {exact: true});
    this.clusterHealthyStatusText = page.getByText('Healthy', {exact: true});
    this.clusterHealthyIndicatorSelector = '[class="healthy healthIndicator"]';
    this.clusterGenerationCompleteMessage = page.getByText(
      'Test Cluster is healthy and ready to use',
    );
    this.clustersBreadcrumb = page
      .getByLabel('Breadcrumb')
      .getByRole('link', {name: 'Clusters'});
    this.deleteClusterButton = page.getByRole('button', {name: 'Delete'});
    this.confirmDeleteInput = page.getByRole('textbox', {
      name: 'Type the word DELETE to confirm',
    });
    this.dangerDeleteButton = page.getByRole('button', {name: 'danger Delete'});
    this.testClusterLink = page.getByRole('link', {name: 'Test Cluster'});
    this.connectorSecretsTab = page.getByRole('tab', {
      name: 'Connector Secrets',
    });
    this.createAClusterButton = page.getByRole('button', {
      name: 'Create a Cluster',
    });
    this.clusterVersionOption = page
      .locator('label')
      .filter({hasText: `${process.env.CLUSTER_VERSION}`})
      .locator('span')
      .first();
    this.replicatedPerformanceCluster = page.locator('label').filter({
      hasText: 'G3-S HA Replicated Performance Verification',
    });
    this.alphaTab = page.getByRole('tab', {name: 'Alpha'});
    this.devTab = page.getByRole('tab', {name: 'Internal Dev'});
    this.stableTab = page.getByRole('tab', {name: 'Stable'});
    this.qaGenerationsTab = page.getByRole('tab', {name: 'QA Generations'});
    this.gcpRegion = page.getByRole('tab', {
      name: 'GCP',
    });
  }

  async clickCreateNewClusterButton(): Promise<void> {
    try {
      const urlCheckPromise = this.page.waitForURL(/\/clusters/, {
        timeout: 60000,
      });
      await urlCheckPromise;
    } catch (error) {
      await this.page
        .getByRole('banner')
        .getByRole('link', {name: 'Clusters'})
        .click();
    }

    let deleteButtons = await this.deleteClusterButton.all();

    while (deleteButtons.length > 0) {
      const deleteButton = deleteButtons[0];
      if (await deleteButton.isVisible()) {
        try {
          await deleteButton.click({force: true, timeout: 60000});
        } catch (error) {
          await this.page
            .getByRole('banner')
            .getByRole('link', {name: 'Clusters'})
            .click();
          await expect(deleteButton).toBeVisible({timeout: 60000});
          await deleteButton.click({force: true, timeout: 60000});
        }

        await this.confirmDeleteInput.click({force: true, timeout: 60000});
        await this.confirmDeleteInput.fill('DELETE', {timeout: 60000});
        const dangerDeleteButtons = await this.dangerDeleteButton.all();
        const lastDangerDeleteButton =
          dangerDeleteButtons[dangerDeleteButtons.length - 1];
        await lastDangerDeleteButton.click();
        await expect(this.page.getByText('Deleting...')).not.toBeVisible({
          timeout: 60000,
        });

        // Refresh the list of delete buttons
        deleteButtons = await this.deleteClusterButton.all();
      }
    }

    const sleep = (ms: number | undefined) =>
      new Promise((r) => setTimeout(r, ms));
    await sleep(20000);

    if (await this.createAClusterButton.isVisible({timeout: 60000})) {
      await this.createAClusterButton.click();
    }

    const maxRetries = 3;

    for (let retries = 0; retries < maxRetries; retries++) {
      try {
        if (retries === 0) {
          await this.createNewClusterButton.click({timeout: 60000});
        } else {
          await this.page
            .getByRole('banner')
            .getByRole('link', {name: 'Clusters'})
            .click({timeout: 60000});
          await this.createNewClusterButton.click({timeout: 60000});
        }
        return;
      } catch (error) {
        console.error(`Click attempt ${retries + 1} failed: ${error}`);
        await new Promise((resolve) => setTimeout(resolve, 10000));
      }
    }

    throw new Error(
      `Failed to click the create a new cluster button after ${maxRetries} attempts.`,
    );
  }

  async clickClusterNameInput(): Promise<void> {
    const maxRetries = 3;

    for (let retries = 0; retries < maxRetries; retries++) {
      try {
        if (retries === 0) {
          await this.clusterNameInput.click({timeout: 30000});
        } else {
          await this.page
            .getByRole('banner')
            .getByRole('link', {name: 'Clusters'})
            .click({timeout: 30000});
          await this.clusterNameInput.click({timeout: 30000});
        }
        return;
      } catch (error) {
        console.error(`Click attempt ${retries + 1} failed: ${error}`);
        await new Promise((resolve) => setTimeout(resolve, 10000));
      }
    }

    throw new Error(
      `Failed to click the create a new cluster button after ${maxRetries} attempts.`,
    );
  }

  async fillClusterNameInput(name: string): Promise<void> {
    await this.clusterNameInput.fill(name);
  }

  async clickCreateClusterButton(): Promise<void> {
    await this.createClusterButton.click();
  }

  async clickClustersBreadcrumb(): Promise<void> {
    try {
      await this.clustersBreadcrumb.click({timeout: 60000});
      await expect(this.clusterCreatingStatusText).toBeVisible({
        timeout: 120000,
      });
    } catch (error) {
      await this.page
        .getByRole('banner')
        .getByRole('link', {name: 'Clusters'})
        .click({timeout: 60000});
      await expect(this.clusterCreatingStatusText).toBeVisible({
        timeout: 120000,
      });
    }
  }

  async clickTestClusterLink(): Promise<void> {
    const maxRetries = 3;

    for (let retries = 0; retries < maxRetries; retries++) {
      try {
        if (retries === 0) {
          await this.testClusterLink.click({timeout: 60000});
        } else {
          await this.page
            .getByRole('banner')
            .getByRole('link', {name: 'Clusters'})
            .click({timeout: 60000});
          await this.testClusterLink.click({timeout: 60000});
        }
        return;
      } catch (error) {
        await this.page.reload();
        console.error(`Click attempt ${retries + 1} failed: ${error}`);
        await new Promise((resolve) => setTimeout(resolve, 10000));
      }
    }

    throw new Error(
      `Failed to click the test cluster link link after ${maxRetries} attempts.`,
    );
  }

  async clickConnectorSecretesTab(): Promise<void> {
    const maxRetries = 3;

    for (let retries = 0; retries < maxRetries; retries++) {
      try {
        await this.connectorSecretsTab.click();
        return;
      } catch (error) {
        console.error(`Click attempt ${retries + 1} failed: ${error}`);
        if (retries === 0) {
          try {
            await this.page
              .getByRole('banner')
              .getByRole('link', {name: 'Clusters'})
              .click();
            await this.clickTestClusterLink();
          } catch (error) {
            console.error(`Camunda apps click attempt failed: ${error}`);
          }
        }
        await new Promise((resolve) => setTimeout(resolve, 1000));
      }
    }
    throw new Error(
      `Failed to click the tasklist link after ${maxRetries} attempts.`,
    );
  }

  async assertClusterHealthyStatusWithRetry(
    timeout: number = 140000,
    maxRetries: number = 3,
    retryDelay: number = 20000,
  ): Promise<void> {
    for (let attempt = 0; attempt < maxRetries; attempt++) {
      try {
        await this.page.reload();
        await expect(this.clusterHealthyStatusText).toBeVisible({
          timeout: timeout,
        });
        return;
      } catch (error) {
        if (attempt < maxRetries - 1) {
          console.warn(`Attempt ${attempt + 1} failed. Retrying...`);
          await new Promise((resolve) => setTimeout(resolve, retryDelay));
        } else {
          throw new Error(`Assertion failed after ${maxRetries} attempts`);
        }
      }
    }
  }

  async clickClusterOption(): Promise<void> {
    const tabs = [
      this.clickDevTab,
      this.clickQAGenerationsTab,
      this.clickStableTab,
      this.clickAlphaTab,
    ];

    for (const clickTab of tabs) {
      try {
        await clickTab.call(this);
        await this.clusterVersionOption.click({timeout: 60000});
        return;
      } catch (error) {
        console.error('Error clicking tab or cluster version option:', error);
      }
    }

    throw new Error('Failed to find cluster generation');
  }

  async clickReplicatedPerformanceClusterType(): Promise<void> {
    if (process.env.IS_PROD !== 'true') {
      await this.replicatedPerformanceCluster.click({timeout: 90000});
    }
  }

  async clickAlphaTab(): Promise<void> {
    await this.alphaTab.click();
  }

  async clickDevTab(): Promise<void> {
    await this.devTab.click();
  }

  async clickStableTab(): Promise<void> {
    await this.stableTab.click();
  }

  async clickQAGenerationsTab(): Promise<void> {
    await this.qaGenerationsTab.click();
  }

  async clickGCPRegion(): Promise<void> {
    const regionSelectionEnabled = process.env.REGION_SELECTION === 'true';
    if (regionSelectionEnabled) {
      await expect(this.gcpRegion).toBeVisible({timeout: 40000});
      await this.gcpRegion.click();
    }
  }
}
export {ClusterPage};
