const { Client } = require('pg')

const connect = async () => {
	const client = new Client({
		host: 'storedb',
		port: 5432,
		user: 'postgres',
		password: 'password',
		database: 'storedb',
	})

	try {
		await client.connect()
		console.log('[PostgreSQL]: Conexión exitosa a la base de datos')

		return client
	} catch (error) {
		console.log('[PostgreSQL]: No se pudo conectar a la base de datos')
		console.error(error)
		// process.exit(1)
	}
}

module.exports = {
	connect,
}
