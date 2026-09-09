import http from 'k6/http';
import { Trend } from 'k6/metrics';
import { sleep } from 'k6';
import { Counter } from 'k6/metrics';

import redis from 'k6/x/redis';

//const redisClient = new redis.Client('redis://redis:6379');
const redisClient = new redis.Client('redis://localhost:6379');
const waiting_time = new Trend('waiting_time');

let token = {
  accessToken: null,
  expiryDate: 0,
  fetchAuthToken: function() {
    this.accessToken = __ENV.TOKEN;
    return;
    const token_url = __ENV.ZEEBE_AUTHORIZATION_SERVER_URL;
    const client_id = __ENV.ZEEBE_CLIENT_ID;
    const client_secret = __ENV.ZEEBE_CLIENT_SECRET;
    const audience = __ENV.ZEEBE_TOKEN_AUDIENCE;

    console.log(`Fetching authentication token from ${token_url}`);

    const payload = {
      grant_type: 'client_credentials',
      audience: audience,
      client_id: client_id,
      client_secret: client_secret,
    };

    const response = http.post(token_url, payload);
    if (response.status != 200) {
      const result = response.body;
      throw new Error(`unable to fetch token: ${result}`);
    }

    this.accessToken = response.json('access_token');
    this.expiryDate = Date.now() + response.json('expires_in') * 1000;

    console.log(`Authentication token fetched successfully.`);
  },
  renew: function() {
    if (this.expiryDate > Date.now()) {
      return this;
    }
    this.fetchAuthToken();
    return this;
  },
};

export const options = {
  scenarios: {
    starter: {
      exec: 'starter',
      executor: 'constant-vus',
      duration: '1d',
      vus: 1,
    },
    reader: {
      exec: 'reader',
      executor: 'constant-vus',
      duration: '1d',
      vus: 2,
    },
  },
};

export async function starter(context) {
  const renewedToken = token.renew();
  const params = {
    headers: {
      'Content-Type': 'application/json',
      'Authorization': "Bearer " + renewedToken.accessToken,
    }
  };


  const payload = JSON.stringify({
    processDefinitionId: "benchmark",
    processDefinitionVersion: 1,
    variables: {},
  });

  const url = __ENV.ZEEBE_REST_ADDRESS + '/v2/process-instances';
  const response = http.post(url, payload, params);
  console.log(`Started process instance at ${url}. Response: ${response.status} (${response.headers}): ${response.body}`);
  const processInstanceKey = response.json('processInstanceKey');
  const now = Date.now();

  await redisClient.lpush('processInstances', JSON.stringify({
    processInstanceKey: processInstanceKey,
    startTime: now,
  }));
  sleep(5);
};


export async function reader(context) {
  const renewedToken = token.renew();
  const params = {
    headers: {
      'Content-Type': 'application/json',
      'Authorization': "Bearer " + renewedToken.accessToken,
    }
  };

  let payload = {
    filter: {
      state: "ACTIVE",
      "$or": [],
    },
  };

  let ids = new Map();

  for (let i = 0; i < 10; i++) {
    let pi = null;
    try {
      pi = await redisClient.rpop('processInstances');
    } catch (error) {
      // "Uncaught (in promise) redis: nil
      //console.error(`Error popping process instance from Redis: ${error}`);
      break;
    }

    const x = JSON.parse(pi);
    //console.log(`Popped process instance from Redis: ${JSON.stringify(x)}`);
    ids.set(x.processInstanceKey, x);
  }

  if (ids.size === 0) {
    //console.log(`No process instances found in Redis. Skipping search.`);
    sleep(5);
    return;
  }

  //console.log(`Found from Redis: ${JSON.stringify(Array.from(ids.keys()))}`);

  for (const key of ids.keys()) {
    payload["filter"]["$or"].push({
      "processInstanceKey": key,
    });
  }


  //console.log(`Payload: ${JSON.stringify(payload)}`);

  const url = __ENV.ZEEBE_REST_ADDRESS + '/v2/process-instances/search';
  const response = http.post(url, JSON.stringify(payload), params);
  //console.log(`Search process instances at ${url}. Response: ${response.status} (${response.headers}): ${response.body}`);

  try {
    for (const processInstance of response.json().items) {
      //console.log(`Process instance found in search response: ${JSON.stringify(processInstance)}`);
      const key = processInstance.processInstanceKey;
      //console.log(`Process instance ${key} found in search response.`);

      const startTime = ids.get(key).startTime;
      const endTime = Date.now();
      const waitingTime = endTime - startTime;
      waiting_time.add(waitingTime);
      console.log(`Process instance ${key} waiting time: ${waitingTime} ms`);
      ids.delete(key);
    }
  } catch (error) {
    console.error(`Error processing response: ${error}`);
    sleep(5);
    return;
  }

  for (const value of ids.values()) {
    // TODO: push back to redis the process instances that were not found in the search response, so they can be searched again later
    await redisClient.lpush('processInstances', JSON.stringify(value));
  }

  sleep(0.1);
};
