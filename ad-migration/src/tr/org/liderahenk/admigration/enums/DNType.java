package tr.org.liderahenk.admigration.enums;

/**
 * This enum is used to indicate which DN entries to consider when processing a
 * request.
 * 
 * @author <a href="mailto:emre.akkaya@agem.com.tr">Emre Akkaya</a>
 *
 */
public enum DNType {

	AHENK(1), USER(2), GROUP(3), ALL(4), ORGANIZATIONAL_UNIT(5);

	private int id;

	private DNType(int id) {
		this.id = id;
	}

	public int getId() {
		return id;
	}

	/**
	 * Provide mapping enums with a fixed ID in JPA (a more robust alternative
	 * to EnumType.String and EnumType.Ordinal)
	 * 
	 * @param id
	 * @return related SessionEvent enum
	 * @see http://blog.chris-ritchie.com/2013/09/mapping-enums-with-fixed-id-in
	 *      -jpa.html
	 * 
	 */
	public static DNType getType(Integer id) {
		if (id == null) {
			return null;
		}
		for (DNType type : DNType.values()) {
			if (id.equals(type.getId())) {
				return type;
			}
		}
		throw new IllegalArgumentException("No matching type for id: " + id);
	}

}
