#!/usr/bin/env node

const amqp = require('amqplib/callback_api')
const postgres = require('./postgres')
const constants = require('./constants')
const utils = require('./utils')

const main = async () => {
	const db = await postgres.connect()

	amqp.connect(constants.RABBIT_URL, (connectionError, connection) => {
		if (connectionError) {
		    console.log('[StoreService]: No se pudo conectar a RabbitMQ')
			console.error(connectionError)
			process.exit(1)
		}

		connection.createChannel((channelError, channel) => {
			if (channelError) {
				console.log('[StoreService]: No se pudo crear el canal')
				console.error(channelError)
				process.exit(1)
			}

			channel.assertQueue(constants.RABBIT_STORE_QUEUE, {
				durable: false,
			})

			channel.prefetch(1)

			console.log('\n[StoreService]: Esperando una nueva venta')

			channel.consume(constants.RABBIT_STORE_QUEUE, async (message) => {
				const salesServiceMessage = message.content.toString()

				console.log(`\n[SalesService]: ${salesServiceMessage}`)

				const storeServiceMessage = await utils.processMessage(
					db,
					salesServiceMessage
				)

				console.log(`[StoreService]: ${storeServiceMessage}`)

				channel.sendToQueue(
					message.properties.replyTo,
					Buffer.from(storeServiceMessage),
					{
						correlationId: message.properties.correlationId,
					}
				)

				channel.ack(message)
			})
		})
	})
}

main()
