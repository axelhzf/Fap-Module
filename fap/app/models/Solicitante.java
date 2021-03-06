package models;

import java.util.*;
import javax.persistence.*;
import play.Logger;
import play.db.jpa.JPA;
import play.db.jpa.Model;
import play.data.validation.*;
import org.joda.time.DateTime;
import models.*;
import messages.Messages;
import validation.*;
import audit.Auditable;
import java.text.ParseException;
import java.text.SimpleDateFormat;

// === IMPORT REGION START ===
import controllers.fap.AgenteController;

// === IMPORT REGION END ===

@Auditable
@Entity
public class Solicitante extends Persona {
	// Código de los atributos

	@Embedded
	public Direccion domicilio;

	public String telefonoFijo;

	public String telefonoMovil;

	public String fax;

	@Email
	public String email;

	public String web;

	public Boolean representado;

	@OneToOne(cascade = CascadeType.ALL, fetch = FetchType.LAZY)
	public RepresentantePersonaFisica representante;

	@OneToMany(cascade = CascadeType.ALL, fetch = FetchType.LAZY)
	@JoinTable(name = "solicitante_representantes")
	public List<RepresentantePersonaJuridica> representantes;

	public Boolean autorizaFuncionario;

	public Solicitante() {
		init();
	}

	public void init() {
		super.init();

		if (domicilio == null)
			domicilio = new Direccion();

		if (representante == null)
			representante = new RepresentantePersonaFisica();
		else
			representante.init();

		if (representantes == null)
			representantes = new ArrayList<RepresentantePersonaJuridica>();

	}

	// === MANUAL REGION START ===
	public Boolean getRepresentado() {
		if (representado != null)
			return representado;
		return false;
	}

	public Boolean getAutorizaFuncionario() {
		if (autorizaFuncionario != null)
			return autorizaFuncionario;
		return false;
	}

	public List<Firmante> calcularFirmantes() {
		//if(solicitante == null) throw new NullPointerException();
		//if(firmantes == null) throw new NullPointerException();
		List<Firmante> firmantes = new ArrayList<Firmante>();
		//Solicitante de la solicitud
		Firmante firmanteSolicitante = new Firmante(this, "unico");
		firmantes.add(firmanteSolicitante);

		//Comprueba los representantes
		if (this.isPersonaFisica() && this.representado) {
			// Representante de persona física
			Firmante representante = new Firmante(this.representante, "representante", "unico");
			firmantes.add(representante);
		} else if (this.isPersonaJuridica()) {
			//Representantes de la persona jurídica
			for (RepresentantePersonaJuridica r : this.representantes) {
				String cardinalidad = null;
				if (r.tipoRepresentacion.equals("mancomunado")) {
					cardinalidad = "multiple";
				} else if ((r.tipoRepresentacion.equals("solidario")) || (r.tipoRepresentacion.equals("administradorUnico"))) {
					cardinalidad = "unico";
				}
				Firmante firmante = new Firmante(r, "representante", cardinalidad);
				firmantes.add(firmante);
			}
		}
		return firmantes;
	}
	// === MANUAL REGION END ===

}
