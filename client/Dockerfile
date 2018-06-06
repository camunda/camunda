FROM node:8.11.2-alpine AS build
WORKDIR /usr/src/app
COPY scripts ./scripts
COPY package.json yarn.lock ./
RUN mkdir -p src/modules && yarn
COPY . ./
RUN yarn build

FROM nginx:1.15.0-alpine
COPY --from=build /usr/src/app/build /usr/share/nginx/html
