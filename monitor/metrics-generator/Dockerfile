FROM node:22-alpine
WORKDIR /app
COPY package.json .
RUN npm install --omit=dev
COPY generator.js .
EXPOSE 9400
CMD ["node", "generator.js"]
