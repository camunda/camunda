/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

export function generateRandomStringAsync(length: number): Promise<string> {
  // Simulate an asynchronous operation (e.g., using setTimeout)
  return new Promise<string>((resolve) => {
    setTimeout(() => {
      const alphabet = 'abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ';
      let result = '';
      for (let i = 0; i < length; i++) {
        const randomIndex = Math.floor(Math.random() * alphabet.length);
        result += alphabet.charAt(randomIndex);
      }
      resolve(result);
    }, 100); // Simulate a delay of 100 milliseconds
  });
}
