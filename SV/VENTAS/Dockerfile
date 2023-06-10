# Utiliza la imagen base de Debian
FROM python:3.10

# Instala Python y otras dependencias
RUN apt-get update && apt-get upgrade -y && \
    apt-get install -y python3 python3-pip

# Establece el directorio de trabajo dentro del contenedor
WORKDIR /app

# Copia los archivos necesarios al contenedor
COPY salesClient.py /app
COPY salesService.py /app

# Instala las dependencias de Python (si las hay)
COPY requirements.txt /app

RUN pip install --no-cache-dir -r requirements.txt

EXPOSE 5672

# Define el comando por defecto que se ejecutará al iniciar el contenedor
CMD ["python3", "-u", "./salesService.py"]
