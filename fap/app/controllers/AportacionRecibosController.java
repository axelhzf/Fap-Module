package controllers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import models.Documento;
import play.mvc.Util;
import tables.TableRenderResponse;
import controllers.gen.AportacionRecibosControllerGen;

public class AportacionRecibosController extends AportacionRecibosControllerGen {

	public static void tablarecibosAportados(Long idSolicitud) {
	    List<Documento> rows = Documento
				.find("select registradas.justificante from Solicitud solicitud " +
					  "join solicitud.aportaciones.registradas registradas " +
					  "where solicitud.id=?",
					  idSolicitud).fetch();
	    TableRenderResponse<Documento> response = TableRenderResponse.sinPermisos(rows);
	    renderJSON(response.toJSON("id", "fechaSubida", "descripcion", "urlDescarga"));
	}

}
