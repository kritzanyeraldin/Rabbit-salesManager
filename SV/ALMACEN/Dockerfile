# Utiliza la imagen base de alpine
FROM node:18-alpine

# Establece el directorio de trabajo dentro del contenedor
WORKDIR /app

# Copia los archivos necesarios al contenedor
COPY package.json package-lock.json ./
RUN npm install
COPY . .


EXPOSE 5672

# Define el comando por defecto que se ejecutará al iniciar el contenedor
CMD ["node", "./storeService.js"]
