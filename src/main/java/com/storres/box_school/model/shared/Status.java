package com.storres.box_school.model.shared;

/**
 * Estado administrativo del estudiante (lo gestiona el admin).
 * La membresia vencida NO es un estado: se deriva de expirationDate &lt; hoy.
 */
public enum Status {
    ACTIVE,
    INACTIVE
}
