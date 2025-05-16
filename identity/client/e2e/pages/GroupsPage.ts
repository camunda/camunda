/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { Page, Locator } from "@playwright/test";
import { Paths } from "../utils/paths";
import { relativizePath } from "../utils/relativizePaths";

export class GroupsPage {
  private page: Page;
  readonly groupsList: Locator;
  readonly createGroupButton: Locator;
  readonly editGroupButton: (rowName?: string) => Locator;
  readonly deleteGroupButton: (rowName?: string) => Locator;
  readonly createGroupModal: Locator;
  readonly closeCreateGroupModal: Locator;
  readonly createGroupIdField: Locator;
  readonly createNameField: Locator;
  readonly createDescriptionField: Locator;
  readonly createGroupModalCancelButton: Locator;
  readonly createGroupModalCreateButton: Locator;
  readonly editGroupModal: Locator;
  readonly closeEditGroupModal: Locator;
  readonly editNameField: Locator;
  readonly editDescriptionField: Locator;
  readonly editGroupModalCancelButton: Locator;
  readonly editGroupModalUpdateButton: Locator;
  readonly deleteGroupModal: Locator;
  readonly closeDeleteGroupModal: Locator;
  readonly deleteGroupModalCancelButton: Locator;
  readonly deleteGroupModalDeleteButton: Locator;

  constructor(page: Page) {
    this.page = page;
    // List page
    this.groupsList = page.getByRole("table");
    this.createGroupButton = page
      .getByRole("button", {
        name: /create( a)? group/i,
      })
      .first();
    this.editGroupButton = (rowName) =>
      this.groupsList.getByRole("row", { name: rowName }).getByLabel(/edit/i);
    this.deleteGroupButton = (rowName) =>
      this.groupsList.getByRole("row", { name: rowName }).getByLabel("Delete");

    // Create group modal
    this.createGroupModal = page.getByRole("dialog", {
      name: "Create group",
    });
    this.closeCreateGroupModal = this.createGroupModal.getByRole("button", {
      name: "Close",
    });
    this.createGroupIdField = this.createGroupModal.getByRole("textbox", {
      name: /group id/i,
    });
    this.createNameField = this.createGroupModal.getByRole("textbox", {
      name: /group name/i,
    });
    this.createDescriptionField = this.createGroupModal.getByRole("textbox", {
      name: "Description",
    });
    this.createGroupModalCancelButton = this.createGroupModal.getByRole(
      "button",
      {
        name: "Cancel",
      },
    );
    this.createGroupModalCreateButton = this.createGroupModal.getByRole(
      "button",
      {
        name: /create group/i,
      },
    );

    // Edit group modal
    this.editGroupModal = page.getByRole("dialog", { name: /edit group/i });
    this.closeEditGroupModal = this.editGroupModal.getByRole("button", {
      name: "Close",
    });
    this.editNameField = this.editGroupModal.getByRole("textbox", {
      name: /group name/i,
    });
    this.editDescriptionField = this.editGroupModal.getByRole("textbox", {
      name: "Description",
    });
    this.editGroupModalCancelButton = this.editGroupModal.getByRole("button", {
      name: "Cancel",
    });
    this.editGroupModalUpdateButton = this.editGroupModal.getByRole("button", {
      name: /edit group/i,
    });

    // Delete group modal
    this.deleteGroupModal = page.getByRole("dialog", { name: /delete group/i });
    this.closeDeleteGroupModal = this.deleteGroupModal.getByRole("button", {
      name: "Close",
    });
    this.deleteGroupModalCancelButton = this.deleteGroupModal.getByRole(
      "button",
      {
        name: "Cancel",
      },
    );
    this.deleteGroupModalDeleteButton = this.deleteGroupModal.getByRole(
      "button",
      {
        name: /delete group/i,
      },
    );
  }

  async navigateToGroups() {
    await this.page.goto(relativizePath(Paths.groups()));
  }
}
