FROM mcr.microsoft.com/playwright:v1.50.0-noble 

WORKDIR /e2e
COPY package*.json ./
COPY playwright.config.ts ./
RUN npm ci                                     

COPY tests/ ./tests/

# Install Playwright browsers once more in case the base image drifts
RUN npx playwright install --with-deps

ENTRYPOINT ["npx","playwright","test","--reporter=html,junit"]
