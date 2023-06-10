import pika
import psycopg2
from salesClient import salesRpcClient


connection = pika.BlockingConnection(
    pika.ConnectionParameters(host='rabbit-broker'))

channel = connection.channel()

channel.queue_declare(queue='sales')

def save_sale(sale):
    # Conexion a la data de sales
    conn = psycopg2.connect(
        host='salesdb',
        port=5432,
        database='salesdb',
        user='postgres',
        password='password')

    cursor = conn.cursor()
    # Guardar datos salesman,customer,email,description,total

    cursor.execute('INSERT INTO sales (salesman,customer,email,description,total) VALUES (%s, %s, %s, %s, %s)', sale)
    conn.commit()
    cursor.close()
    conn.close()
    sale = '/'.join(item for item in sale)
    return sale

def bill(data_from_seller,data_from_store):
    # recibe name_salesman/id_product:name_product:price
    data_from_store=data_from_store.split('/')
    print(data_from_store)


    # Separa productcription (id,quantity)


    # Separa name_salesman y los productos
    salesman=data_from_store[0]
    products=data_from_store[1].split(',')

    data_from_seller=data_from_seller.split('/')
    id_salesman=data_from_seller[0]
    customer=data_from_seller[1]
    email=data_from_seller[2]
    data_from_seller=data_from_seller[3].split(',')

    #print(data_from_seller)
    print('inicio',products, type(products))

    # Cada producto sera una sublista de la lista products
    products = [elemento.split(':') for elemento in products]

    print('da',data_from_seller)
    # agrega el precio a los productos que se guardan en product
    for elemento in data_from_seller:
        elemento=elemento.split(':')
        print(elemento)
        if len(elemento)==2:
            id=elemento[0]
            price=elemento[1]
            for product in products:
                if id==product[0]:
                    product.append(price)

    print('fin',products)


    # Calcular el total
    total=0
    for product in products:
        price=float(product[2])
        quantity=float(product[3])
        total+=price*quantity

    print(total)

    # Prepara la lista product para ser pasado como string
    products = [':'.join(item) for item in products]
    products=','.join(products)

    print(products)

    # Datos que se guardaran en salesdb
    sale=(salesman,customer,email,products,str(total))
    print(sale)

    # Si no hay error se guardan
    response =save_sale(sale)
    return response


# Recibe entradas de vendedor
def on_request(ch, method, props, body):

    # Data enviada desde el vendedor
    data_from_seller=body.decode('utf-8')
    print(" [.] process message(%s)" % data_from_seller)
    data_to_send=data_from_seller
    data_to_send = data_to_send.split('/')
    data_to_send.remove(data_to_send[1])
    data_to_send.remove(data_to_send[1])
    data_to_send = '/'.join(data_to_send)

    # Crear el cliente de Sales para que envie la data a almacen y haga la validacion
    salesRpc=salesRpcClient()
    print(f' [x] Requesting validation({data_to_send})')
    # Se guarda la respuesta que ha sido enviada del almacen(Store)
    response_from_store = salesRpc.call(data_to_send)
    print(" [.] Got %r" % response_from_store)

    # Respuesta que se va enviar al vendedor
    response = '\0'
    # Si pasa la validacion se procede con el proceso de la factura sino retorna un error
    if 'Error' not in response_from_store:
        response=bill(data_from_seller,response_from_store)
    else:
        response = response_from_store


    ch.basic_publish(exchange='',
                     routing_key=props.reply_to,
                     properties=pika.BasicProperties(correlation_id = \
                                                         props.correlation_id),
                     body=str(response))
    ch.basic_ack(delivery_tag=method.delivery_tag)


channel.basic_qos(prefetch_count=1)
channel.basic_consume(queue='sales', on_message_callback=on_request)

print(" [x] Awaiting RPC requests")
channel.start_consuming()