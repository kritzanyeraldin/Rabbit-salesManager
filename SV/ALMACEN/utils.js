const parseData = (data) => {
	const splittedData = data.split('/')
	const userID = parseInt(splittedData[0].trim())
	const products = splittedData[1].split(',').reduce((result, p) => {
		const product = p.trim().split(':')
		const key = product[0]
		const value = parseInt(product[1])

		result[key] = value
		return result
	}, {})

	return {
		userID,
		products,
	}
}

const processMessage = async (db, message) => {
	const { userID, products } = parseData(message)

	try {
		const userQuery = `SELECT * FROM users WHERE id = ${userID}`
		const userResult = await db.query(userQuery)

		// * Validate userID
		const nonExistentUser = userResult.rowCount === 0

		if (nonExistentUser) return 'Error: El usuario no existe'

		const user = userResult.rows[0]

		const productIDs = Object.keys(products).map((id) => parseInt(id))
		const joinedProductIDs = productIDs.join(', ')
		const productsQuery = `SELECT * FROM product WHERE id IN (${joinedProductIDs})`
		const productsResult = await db.query(productsQuery)

		// * Validate product IDs
		const areProductsComplete = productsResult.rowCount === productIDs.length

		if (!areProductsComplete) {
			let message = 'Error: No se encontró ningún producto'

			if (productsResult.rows.length > 0) {
				const foundProductIDs = productsResult.rows.map((p) => p.id)
				const joinedNotFoundProductIDs = productIDs
					.filter((p) => !foundProductIDs.includes(p))
					.join(', ')
				message = `Error: No se encontró los productos con ID={${joinedNotFoundProductIDs}}`
			}

			return message
		}

		// * Validate if stock is sufficient
		const insufficientProducts = productsResult.rows.filter(
			(p) => parseInt(p.stock) < products[p.id]
		)

		if (insufficientProducts.length > 0) {
			const joinedInsufficientProductIDs = insufficientProducts
				.map((p) => p.id)
				.join(', ')
			const message = `Error: Insuficiente stock en los productos con ID={${joinedInsufficientProductIDs}}`

			return message
		}

		// * Update stock in database
		await Promise.all(
			productsResult.rows.map((p) => {
				const currentStock = parseInt(p.stock)
				const quantityToBuy = products[p.id]
				const updatedStock = (currentStock - quantityToBuy).toString()

				return db.query(
					`UPDATE product SET stock = ${updatedStock} WHERE id = ${p.id}`
				)
			})
		)

		const productsMessage = productsResult.rows
			.map((p) => `${p.id}:${p.name}:${p.price}`)
			.join(',')

		const message = `${user.name}/${productsMessage}`
		return message
	} catch (error) {
		const message = 'Error: Ocurrió un error al intentar procesar los datos'
		console.error(error)
		return message
	}
}

module.exports = {
	parseData,
	processMessage,
}
