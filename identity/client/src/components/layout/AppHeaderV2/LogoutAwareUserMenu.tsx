/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type { ReactNode } from "react";
import {
  Avatar,
  Button,
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
  UserMenu,
  type UserMenuItem,
} from "@camunda/design-system";

type LogoutAwareUserMenuProps = {
  userName: string;
  userEmail: string;
  items: UserMenuItem[];
  canLogout: boolean;
  onLogout: () => void;
  customSection: ReactNode;
};

const LogoutAwareUserMenu = ({
  userName,
  userEmail,
  items,
  canLogout,
  onLogout,
  customSection,
}: LogoutAwareUserMenuProps) => {
  if (canLogout) {
    return (
      <UserMenu
        userName={userName}
        userEmail={userEmail}
        items={items}
        onLogout={onLogout}
        customSection={customSection}
      />
    );
  }

  return (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        <Button
          type="button"
          variant="ghost"
          size="icon"
          className="rounded-full"
          aria-label={`User menu, ${userName}`}
        >
          <Avatar name={userName} size="sm" aria-hidden="true" />
        </Button>
      </DropdownMenuTrigger>
      <DropdownMenuContent className="w-56" align="end">
        <DropdownMenuLabel className="font-normal">
          <div className="flex flex-col gap-0.5">
            <span className="font-semibold">{userName}</span>
            <span className="text-xs text-neutral-foreground-subtle">
              {userEmail}
            </span>
          </div>
        </DropdownMenuLabel>
        {customSection}
        <DropdownMenuSeparator />
        {items.map((item) => (
          <DropdownMenuItem key={item.key} onClick={item.onClick}>
            {item.label}
          </DropdownMenuItem>
        ))}
      </DropdownMenuContent>
    </DropdownMenu>
  );
};

export { LogoutAwareUserMenu };
