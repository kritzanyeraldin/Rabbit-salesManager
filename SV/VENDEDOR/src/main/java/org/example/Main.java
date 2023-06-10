package org.example;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.Scanner;
import java.util.*;

class RPCClient implements AutoCloseable {
    private Connection connection;
    private Channel channel;
    private String requestQueueName = "sales";

    public RPCClient() throws IOException, TimeoutException {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost("localhost");

        connection = factory.newConnection();
        channel = connection.createChannel();
    }

    public static void print(String response){
        if (!response.contains("Error")){
            String [] datos = response.split("/");

            // nombre_vendedor/name_cliente/email/descripcion/total  descripcion=id:name:cantidad:precio,id2:name2:cantidad2:precio2
            String name_salesman=datos[0], client=datos[1], email_client=datos[2], description=datos[3], total=datos[4];

            // Se trata los datos de la descripcion de los productos
            String [] productos = description.split(",");
            String[][] subarreglos = new String[productos.length][];

            for (int i = 0; i < productos.length; i++) {
                subarreglos[i] = productos[i].split(":");
            }


            // Se imprime la factura
            System.out.println("------------------------------------------------------");
            System.out.println("Vendedor: "+ name_salesman);
            System.out.printf("%s%s%30s%s%n","Cliente: ", client, "e-mail: ",email_client);
            // Imprimir los subarreglos
            for (String[] subarreglo : subarreglos) {
                //System.out.println(Arrays.toString(subarreglo));
                System.out.printf("%s%s%15s%s%25s%s%n%s%s%n","id: ", subarreglo[0], "producto: ",subarreglo[1],"precioxund: ",subarreglo[2], "cantidad: ",subarreglo[3]);
            }
            System.out.printf("%42s%10s%n","TOTAL:", total);
            System.out.println("------------------------------------------------------");
        }
        else{
            System.out.println(response);
        }

    }

    public static void main(String[] argv) {
        try (RPCClient processMessageRpc = new RPCClient()) {
            // Crear un objeto Scanner para leer la entrada del usuario
            Scanner scanner = new Scanner(System.in);


            boolean salir = false;
            int option; //Guardaremos la opcion del usuario

            while (!salir) {

                System.out.println("1. Opcion 1");
                System.out.println("2. Opcion 2");
                System.out.println("3. Salir");
                try {
                    System.out.println("Escribe una de las opciones");
                    option = scanner.nextInt();
                    switch (option) {
                        case 1:
                            int numThreads = 1000;

                            for (int i = 0; i < numThreads; i++) {
                                int finalI = i;
                                Thread thread = new Thread(() -> {
                                    // Formato de envio del mensaje id_salesman/customer/email/id1:3,id2:4
                                    String sales = "1/cliente" + (finalI + 1) + "/" + "cliente" + (finalI + 1) + "@gmail.com" + "/" + "1:1";
                                    System.out.println(" [x] Requesting processMessage(" + sales + ")");
                                    try {
                                        String response = processMessageRpc.call(sales);
                                        System.out.println(" [.] Got message " + response);
                                    } catch (IOException | InterruptedException | ExecutionException e) {
                                        throw new RuntimeException(e);
                                    }

                                    //RPCClient.print(response);
                                });
                                thread.start();
                            }
                            break;
                        case 2:

                            // Pedir al usuario que ingrese su id
                            System.out.println("Ingrese id de vendedor: ");
                            String id_salesman = scanner.next();

                            // Pedir al usuario que ingrese nombre del cliente
                            System.out.println("Ingrese nombre del cliente: ");
                            String name_customer = scanner.next();

                            //Pedir al vendedor que ingrese email del cliente
                            System.out.println("Ingrese email del cliente (max 50 caracteres)");
                            String email_client = scanner.next();

                            // Pedir al vendedor que ingrese los productos y su cantidad
                            System.out.println("Ingrese los productos y su cantidad:\nPor ejemplo: id1:cantidad1,id2:cantidad2,...");
                            String description = scanner.next();

                            // Cerrar el objeto Scanner
                            scanner.close();

                            // Formato de envio del mensaje id_salesman/customer/email/id1:3,id2:4
                            String sales = id_salesman + "/" + name_customer + "/" + email_client + "/" + description;

                            // manda el mensaje a sales
                            System.out.println(" [x] Requesting processMessage(" + sales + ")");
                            String response = processMessageRpc.call(sales);
                            System.out.println(" [.] Got message");
                            RPCClient.print(response);
                            salir=true;
                            break;
                        case 3:
                            salir=true;
                            break;
                    }
                } catch (InputMismatchException e) {
                    System.out.println("Debes insertar un número");
                    scanner.next();}


            }
        }catch(IOException | TimeoutException | InterruptedException | ExecutionException e){
            e.printStackTrace();}
    }

    public String call(String message) throws IOException, InterruptedException, ExecutionException {
        final String corrId = UUID.randomUUID().toString();

        String replyQueueName = channel.queueDeclare().getQueue();
        AMQP.BasicProperties props = new AMQP.BasicProperties
                .Builder()
                .correlationId(corrId)
                .replyTo(replyQueueName)
                .build();

        channel.basicPublish("", requestQueueName, props, message.getBytes("UTF-8"));

        final CompletableFuture<String> response = new CompletableFuture<>();

        String ctag = channel.basicConsume(replyQueueName, true, (consumerTag, delivery) -> {
            if (delivery.getProperties().getCorrelationId().equals(corrId)) {
                response.complete(new String(delivery.getBody(), "UTF-8"));
            }
        }, consumerTag -> {
        });

        String result = response.get();
        channel.basicCancel(ctag);
        return result;
    }

    public void close() throws IOException {
        connection.close();
    }
}