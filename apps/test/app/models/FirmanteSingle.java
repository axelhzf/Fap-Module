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

// === IMPORT REGION END ===

@Entity
public class FirmanteSingle extends Singleton {
	// Código de los atributos

	@OneToMany(cascade = CascadeType.ALL, fetch = FetchType.LAZY)
	@JoinTable(name = "firmantesingle_firmantes")
	public List<Firmante> firmantes;

	public String cadena;

	public String otros;

	public FirmanteSingle() {
		init();
	}

	public void init() {
		super.init();

		if (firmantes == null)
			firmantes = new ArrayList<Firmante>();

	}

	// === MANUAL REGION START ===

	// === MANUAL REGION END ===

}
