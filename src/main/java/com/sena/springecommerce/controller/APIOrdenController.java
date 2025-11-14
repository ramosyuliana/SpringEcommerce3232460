package com.sena.springecommerce.controller;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sena.springecommerce.model.DetalleOrden;
import com.sena.springecommerce.model.Orden;
import com.sena.springecommerce.model.Producto;
import com.sena.springecommerce.model.Usuario;
import com.sena.springecommerce.service.IDetalleOrdenService;
import com.sena.springecommerce.service.IOrdenService;
import com.sena.springecommerce.service.IProductoService;
import com.sena.springecommerce.service.IUsuarioService;

@RestController
@RequestMapping("/apiordenes")
public class APIOrdenController {
	@Autowired
	private IUsuarioService usuarioService;

	@Autowired
	private IOrdenService ordenService;

	@Autowired
	private IProductoService productoservice;

	@Autowired
	private IDetalleOrdenService detalleordenservice;

// Crear una orden con varios detalles
	@PostMapping("/createorden")
	public ResponseEntity<Orden> crearOrdenConDetalles(@RequestBody Orden orden) {

		// Validar que el usuario exista
		if (orden.getUsuario() == null || orden.getUsuario().getId() == null) {
			throw new RuntimeException("Debe asignar un usuario a la orden");
		}

		Usuario usuario = usuarioService.findById(orden.getUsuario().getId())
				.orElseThrow(() -> new RuntimeException("Usuario no encontrado con id: " + orden.getUsuario().getId()));

		orden.setUsuario(usuario);
		Date fechaCreacion = new Date();
		orden.setFechacreacion(fechaCreacion);
		orden.setNumero(ordenService.generarNumeroOrden());
		orden.setTotal(0.0);
		// Guarda una orden sin detalles para obtener la id para guardarlos despues
		Orden ordenguardada = ordenService.save(orden);

		// Crear lista para detalles finales
		List<DetalleOrden> detallesGuardados = new ArrayList<>();

		// Validar y guardar cada detalle
		if (orden.getDetalle() != null) {
			for (DetalleOrden detalle : orden.getDetalle()) {

				// Buscar producto
				Producto producto = productoservice.get(detalle.getProducto().getId()).orElseThrow(
						() -> new RuntimeException("Producto no encontrado con id: " + detalle.getProducto().getId()));

				// Verificar disponibilidad
				if (producto.getCantidad() < detalle.getCantidad()) {
					throw new RuntimeException("No hay suficiente stock del producto: " + producto.getNombre());
				}

				// Actualizar stock
				producto.setCantidad(Integer.valueOf((int) (producto.getCantidad() - detalle.getCantidad())));
				productoservice.save(producto);
				detalle.setProducto(producto);

				// Calcular total del detalle
				detalle.setPrecio(producto.getPrecio());
				detalle.setTotal(producto.getPrecio() * detalle.getCantidad());
				detalle.setOrden(orden);
				detalle.setNombre(producto.getNombre());

				// Guardar detalle
				DetalleOrden detalleGuardado = detalleordenservice.save(detalle);
				detallesGuardados.add(detalleGuardado);
			}
		}

		// Calcular total de la orden
		double totalOrden = detallesGuardados.stream().mapToDouble(DetalleOrden::getTotal).sum();
		ordenguardada.setTotal(totalOrden);

		// Guardar la orden (último paso)
		ordenguardada.setDetalle(detallesGuardados);
		ordenService.save(ordenguardada);

		return ResponseEntity.status(HttpStatus.CREATED).body(ordenguardada);
	}

	// Listar todas las órdenes
	@GetMapping("/list")
	public List<Orden> listarOrdenes() {
		return ordenService.findAll();
	}

	@GetMapping("/orden/{id}")
	public ResponseEntity<Orden> getOrdenByEntity(@PathVariable Integer id) {
		Optional<Orden> orden = ordenService.findById(id);
		return orden.map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
	}
}
