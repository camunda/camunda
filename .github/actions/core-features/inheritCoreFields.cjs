/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

async function copyTeamAndProjectNameFromParent({
  childIssueNumber,
  projectId,
  owner,
  repo,
  github,
}) {
  // Find parent issue number
  const { parentNumber } = await getIssueQuery(
    github,
    owner,
    repo,
    childIssueNumber
  );
  if (!parentNumber) {
    throw new Error(`No parent found for issue #${childIssueNumber}`);
  }
  // Copy only the two fields
  return copyCustomFieldsBetweenIssues({
    sourceIssueNumber: parentNumber,
    targetIssueNumber: childIssueNumber,
    projectId,
    owner,
    repo,
    github,
    allowedFields: ["Team", "PH Project name"],
  });
}

async function copyCustomFieldsBetweenIssues({
  sourceIssueNumber,
  targetIssueNumber,
  projectId,
  owner,
  repo,
  github,
  allowedFields,
}) {
  const projectItemsA = await fetchProjectItemsWithFields({
    issueNumber: sourceIssueNumber,
    owner,
    repo,
    github,
  });
  console.log(
    "[DEBUG] Source project items:",
    JSON.stringify(projectItemsA, null, 2)
  );

  // Inline findProjectItemById for source
  const itemA = projectItemsA.find((item) => item.project?.id === projectId);
  if (!itemA) {
    throw new Error(`Source issue is not part of project with id ${projectId}`);
  }
  let fieldsToCopy = extractFieldsToCopy(itemA.fieldValues?.nodes ?? []);
  if (allowedFields) {
    fieldsToCopy = fieldsToCopy.filter((f) => allowedFields.includes(f.name));
  }
  console.log("[DEBUG] Fields to copy:", JSON.stringify(fieldsToCopy, null, 2));

  const projectItemsB = await fetchProjectItemsWithFields({
    issueNumber: targetIssueNumber,
    owner,
    repo,
    github,
  });
  console.log(
    "[DEBUG] Target project items:",
    JSON.stringify(projectItemsB, null, 2)
  );
  // Inline findProjectItemById for target
  const itemB = projectItemsB.find((item) => item.project?.id === projectId);
  if (!itemB) {
    throw new Error(`Target issue is not part of project with id ${projectId}`);
  }

  // Fetch all field definitions for the project
  const fieldDefs = await fetchProjectFieldDefinitions({ github, projectId });

  const updateResult = await updateCustomFieldsForIssue({
    projectItems: [itemB],
    github,
    fieldsToUpdate: fieldsToCopy,
    fieldDefs,
    projectId,
  });
  console.log("[DEBUG] Update result:", JSON.stringify(updateResult, null, 2));
  return updateResult;
}

async function updateCustomFieldsForIssue({
  projectItems,
  github,
  fieldsToUpdate,
  fieldDefs,
  projectId,
}) {
  const updates = fieldsToUpdate.map(({ name, value }) =>
    updateFieldOnProjectItems(
      projectItems,
      github,
      name,
      value,
      fieldDefs,
      projectId
    )
  );
  return Promise.all(updates);
}

// Helper: extract field name/value pairs from field nodes
function extractFieldsToCopy(fieldNodes) {
  return fieldNodes
    .map((field) => {
      if ("title" in field && !("field" in field)) {
        return { name: "Iteration", value: field.title };
      }
      const name = field.field?.name;
      const value =
        field.text ?? field.name ?? field.number ?? field.date ?? null;
      return { name, value };
    })
    .filter((f) => f.name && f.value !== null);
}

async function fetchProjectItemsWithFields({
  issueNumber,
  owner,
  repo,
  github,
}) {
  const getFieldsQuery = `
    query($owner: String!, $repo: String!, $issueNumber: Int!) {
      repository(owner: $owner, name: $repo) {
        issue(number: $issueNumber) {
          projectItems(first: 10) {
            nodes {
              id
              project { id }
              fieldValues(first: 50) {
                nodes {
                  ... on ProjectV2ItemFieldTextValue { 
                    text
                    field {
                      ... on ProjectV2FieldCommon { id name dataType }
                    }
                  }
                  ... on ProjectV2ItemFieldSingleSelectValue { 
                    name
                    field {
                      ... on ProjectV2FieldCommon { id name dataType }
                      ... on ProjectV2SingleSelectField { 
                        id name dataType 
                        options { id name }
                      }
                    }
                  }
                  ... on ProjectV2ItemFieldNumberValue { 
                    number
                    field {
                      ... on ProjectV2FieldCommon { id name dataType }
                    }
                  }
                  ... on ProjectV2ItemFieldDateValue { 
                    date
                    field {
                      ... on ProjectV2FieldCommon { id name dataType }
                    }
                  }
                  ... on ProjectV2ItemFieldIterationValue { 
                    title
                    field {
                      ... on ProjectV2FieldCommon { id name dataType }
                    }
                  }
                }
              }
            }
          }
        }
      }
    }
  `;
  const variables = { owner, repo, issueNumber };
  const { repository } = await github.graphql(getFieldsQuery, variables);
  return repository?.issue?.projectItems?.nodes ?? [];
}

async function fetchProjectFieldDefinitions({ github, projectId }) {
  const query = `
    query($projectId: ID!) {
      node(id: $projectId) {
        ... on ProjectV2 {
          fields(first: 100) {
            nodes {
              ... on ProjectV2FieldCommon {
                id
                name
                dataType
              }
              ... on ProjectV2IterationField {
                id
                name
                dataType
              }
              ... on ProjectV2SingleSelectField {
                id
                name
                dataType
                options { id name }
              }
            }
          }
        }
      }
    }
  `;
  const { node } = await github.graphql(query, { projectId });
  return node?.fields?.nodes || [];
}

function buildTextFieldUpdateMutation(
  fieldNode,
  projectId,
  projectItemId,
  value
) {
  return {
    mutation: `
      mutation($projectId: ID!, $itemId: ID!, $fieldId: ID!, $value: String!) {
        updateProjectV2ItemFieldValue(input: {
          projectId: $projectId, itemId: $itemId, fieldId: $fieldId, value: { text: $value }
        }) { projectV2Item { id } }
      }
    `,
    input: {
      projectId,
      itemId: projectItemId,
      fieldId: fieldNode.field.id,
      value,
    },
  };
}

function buildNumberFieldUpdateMutation(
  fieldNode,
  projectId,
  projectItemId,
  value
) {
  return {
    mutation: `
      mutation($projectId: ID!, $itemId: ID!, $fieldId: ID!, $value: Float!) {
        updateProjectV2ItemFieldValue(input: {
          projectId: $projectId, itemId: $itemId, fieldId: $fieldId, value: { number: $value }
        }) { projectV2Item { id } }
      }
    `,
    input: {
      projectId,
      itemId: projectItemId,
      fieldId: fieldNode.field.id,
      value: parseFloat(value),
    },
  };
}

function buildDateFieldUpdateMutation(
  fieldNode,
  projectId,
  projectItemId,
  value
) {
  return {
    mutation: `
      mutation($projectId: ID!, $itemId: ID!, $fieldId: ID!, $value: Date!) {
        updateProjectV2ItemFieldValue(input: {
          projectId: $projectId, itemId: $itemId, fieldId: $fieldId, value: { date: $value }
        }) { projectV2Item { id } }
      }
    `,
    input: {
      projectId,
      itemId: projectItemId,
      fieldId: fieldNode.field.id,
      value,
    },
  };
}

function buildSingleSelectFieldUpdateMutation(
  fieldNode,
  projectId,
  projectItemId,
  value,
  fieldDefs
) {
  return {
    mutation: `
      mutation($projectId: ID!, $itemId: ID!, $fieldId: ID!, $optionId: String!) {
        updateProjectV2ItemFieldValue(input: {
          projectId: $projectId, itemId: $itemId, fieldId: $fieldId, value: { singleSelectOptionId: $optionId }
        }) { projectV2Item { id } }
      }
    `,
    input: {
      projectId,
      itemId: projectItemId,
      fieldId: fieldNode.field.id,
      optionId: getSingleSelectOptionId(fieldNode, value, fieldDefs),
    },
  };
}

function getSingleSelectOptionId(fieldNode, value, fieldDefs) {
  if (fieldNode.field && Array.isArray(fieldNode.field.options)) {
    const option = fieldNode.field.options.find((opt) => opt.name === value);
    if (option) return option.id;
  }
  // Fallback to fieldDefs
  const def = fieldDefs.find(
    (f) => f.name === fieldNode.field.name && Array.isArray(f.options)
  );
  if (def) {
    const option = def.options.find((opt) => opt.name === value);
    if (option) return option.id;
  }
  return undefined;
}

function buildFieldUpdateMutation(
  fieldNode,
  projectId,
  projectItemId,
  value,
  fieldDefs
) {
  if (fieldNode.field.dataType === "TEXT") {
    return buildTextFieldUpdateMutation(
      fieldNode,
      projectId,
      projectItemId,
      value
    );
  } else if (fieldNode.field.dataType === "NUMBER") {
    return buildNumberFieldUpdateMutation(
      fieldNode,
      projectId,
      projectItemId,
      value
    );
  } else if (fieldNode.field.dataType === "DATE") {
    return buildDateFieldUpdateMutation(
      fieldNode,
      projectId,
      projectItemId,
      value
    );
  } else if (fieldNode.field.dataType === "SINGLE_SELECT") {
    return buildSingleSelectFieldUpdateMutation(
      fieldNode,
      projectId,
      projectItemId,
      value,
      fieldDefs
    );
  }
  return null;
}

async function updateFieldOnProjectItems(
  projectItems,
  github,
  name,
  value,
  fieldDefs,
  projectId
) {
  let found = false;
  let results = [];
  for (const item of projectItems) {
    const projectItemId = item.id;
    // Use robust lookup: field node or definition
    const fieldNode = findFieldNodeOrDefinition(
      item.fieldValues?.nodes ?? [],
      fieldDefs,
      name
    );
    if (fieldNode && fieldNode.field?.id && fieldNode.field?.dataType) {
      found = true;
      const mutationObj = buildFieldUpdateMutation(
        fieldNode,
        projectId,
        projectItemId,
        value,
        fieldDefs
      );
      if (!mutationObj) {
        results.push({
          name,
          value,
          success: false,
          error: `Unsupported data type: ${fieldNode.field.dataType}`,
        });
        continue;
      }
      try {
        await github.graphql(mutationObj.mutation, mutationObj.input);
        results.push({ name, value, success: true });
      } catch (err) {
        results.push({ name, value, success: false, error: err.message });
      }
    }
  }
  if (found) {
    return results.length === 1 ? results[0] : results;
  }
  return {
    name,
    value,
    success: false,
    error: "Field not found on any project item for this issue.",
  };
}

function findFieldNodeOrDefinition(fieldNodes, fieldDefs, name) {
  // Try to find in fieldValues first
  const node = Array.isArray(fieldNodes)
    ? fieldNodes.find((f) => f.field?.name === name)
    : undefined;
  if (node && node.field?.id && node.field?.dataType) return node;
  // Fallback to field definition
  const def = fieldDefs.find((f) => f.name === name);
  if (def) {
    return { field: def };
  }
  return undefined;
}

async function getIssueQuery(github, owner, repo, issueNumber) {
  const data = await github.graphql(
    `
query($owner:String!,$name:String!,$issueNumber:Int!) {
  repository(owner:$owner,name:$name) {
    issue(number:$issueNumber){
      id
      createdAt
      labels{
        totalCount
      }
      blocking{totalCount}
      parent {
        id
        number
      }
      
    }
    
  }
}
`,
    {
      owner,
      name: repo,
      issueNumber,
    }
  );
  const parent = data.repository.issue.parent;
  return {
    issueGlobalId: data.repository.issue.id,
    parentGlobalId: parent ? parent.id : undefined,
    parentNumber: parent ? parent.number : undefined,
  };
}

async function run({ github, context, core }) {
  try {
    const owner = "camunda";
    const repo = "camunda";
    // this is the global ID for project core features (173)
    const TARGET_PROJECT_ID = "PVT_kwDOACVKPs4A1Kno";
    console.log("context:", JSON.stringify(context));
    let childIssueNumber = context?.payload?.issue?.number;
    if (!childIssueNumber) {
      console.error(
        "No child issue number found!, using test issue number 36974"
      );
      childIssueNumber = 36974;
    }

    const result = await copyTeamAndProjectNameFromParent({
      childIssueNumber,
      projectId: TARGET_PROJECT_ID,
      owner,
      repo,
      github,
    });
  } catch (error) {
    console.error("Failed to run script:", error);
    return;
  }
}

module.exports = run;
