#!/usr/bin/env python3
"""
Smoke test de punta a punta contra una instancia en ejecucion (usa solo la libreria estandar de Python).

ESCRIBE DATOS de prueba (un precio, un estudiante "smoke-<timestamp>@example.test" y sus pagos):
ejecutalo contra una COPIA de la base o contra una base de desarrollo, nunca contra produccion.

Variables de entorno:
  BASE_URL        (default http://localhost:8080)
  ADMIN_USERNAME  usuario administrador existente           (obligatorio)
  ADMIN_PASSWORD  su clave                                  (obligatorio)
  PARALLEL_PAYMENTS  pagos simultaneos al mismo alumno      (default 6)
  EXPECT_SWAGGER     "true" si la instancia corre con perfil dev / SWAGGER_ENABLED=true (default false:
                     entonces Swagger DEBE responder 404)

El script llama ~8 veces a /api/auth/*: arranca la instancia de prueba con AUTH_RATE_LIMIT_PER_MINUTE=1000
para poder repetirlo sin tropezar con el limite (429).

Uso:  ADMIN_USERNAME=... ADMIN_PASSWORD=... BASE_URL=http://localhost:8081 python scripts/smoke_test.py
Sale con codigo 1 si algun chequeo falla.
"""
import datetime
import json
import os
import sys
import threading
import time
import urllib.error
import urllib.request

BASE = os.environ.get("BASE_URL", "http://localhost:8080").rstrip("/")
ADMIN_USER = os.environ.get("ADMIN_USERNAME")
ADMIN_PASS = os.environ.get("ADMIN_PASSWORD")
PARALLEL = int(os.environ.get("PARALLEL_PAYMENTS", "6"))
EXPECT_SWAGGER = os.environ.get("EXPECT_SWAGGER", "false").lower() == "true"

if not ADMIN_USER or not ADMIN_PASS:
    sys.exit("Define ADMIN_USERNAME y ADMIN_PASSWORD (ver docstring).")


def call(method, path, body=None, token=None):
    req = urllib.request.Request(BASE + path, method=method)
    req.add_header("Content-Type", "application/json")
    if token:
        req.add_header("Authorization", "Bearer " + token)
    data = json.dumps(body).encode() if body is not None else None
    try:
        with urllib.request.urlopen(req, data, timeout=30) as r:
            txt = r.read().decode()
            return r.status, (json.loads(txt) if txt else None)
    except urllib.error.HTTPError as e:
        txt = e.read().decode()
        try:
            return e.code, json.loads(txt)
        except ValueError:
            return e.code, txt


results = []


def check(name, cond, extra=""):
    results.append((name, bool(cond)))
    print(("OK    " if cond else "FALLA "), name, extra)


def main():
    stamp = str(int(time.time()))
    email = f"smoke-{stamp}@example.test"
    username = f"smoke{stamp}"

    # --- infraestructura y exposicion
    s, b = call("GET", "/actuator/health/liveness")
    check("sonda liveness publica y UP", s == 200 and b.get("status") == "UP", str(b))
    s, _ = call("GET", "/actuator/health")
    check("health agregado exige autenticacion (no consulta SMTP en anonimo)", s == 401, f"status={s}")
    s, _ = call("GET", "/v3/api-docs")
    if EXPECT_SWAGGER:
        check("swagger encendido (perfil dev)", s == 200, f"status={s}")
    else:
        check("swagger apagado: /v3/api-docs -> 404", s == 404, f"status={s}")

    # --- autenticacion basica
    s, b = call("GET", "/api/students/me")
    check("sin token -> 401", s == 401)
    s, _ = call("GET", "/api/students", token="x.y.z")
    check("token basura -> 401", s == 401)
    s, b1 = call("POST", "/api/auth/login", {"username": ADMIN_USER, "password": "clave-incorrecta"})
    s2, b2 = call("POST", "/api/auth/login", {"username": "no-existe-" + stamp, "password": "clave-incorrecta"})
    check("clave mala y usuario inexistente -> 401 identico", s == 401 and s2 == 401 and b1["message"] == b2["message"])
    s, b = call("POST", "/api/auth/login", {"username": ADMIN_USER, "password": ADMIN_PASS})
    check("login admin", s == 200 and "ROLE_ADMIN" in b.get("roles", []))
    if s != 200:
        sys.exit("No se pudo iniciar sesion como admin; no tiene sentido seguir.")
    adm = b["token"]

    # --- precio y estudiante
    s, b = call("POST", "/api/prices", {"type": "MONTHLY", "amount": 80000, "durationDays": 30}, adm)
    check("crear precio", s == 201)
    s, b = call("POST", "/api/students",
                {"firstName": "Smoke", "lastName": "Test", "email": email.upper(), "phone": "3001234567"}, adm)
    check("crear estudiante (email normalizado a minusculas)", s == 201 and b.get("email") == email, str(b.get("email")))
    sid, code = b["id"], b["registrationCode"]
    exp0 = datetime.date.fromisoformat(b["expirationDate"])

    # --- registro por codigo
    s, _ = call("POST", "/api/auth/register",
                {"username": username, "password": "ClaveSegura123", "email": email, "registrationCode": "AAAAAAAAAA"})
    check("registro con codigo falso -> 400", s == 400)
    s, b = call("POST", "/api/auth/register",
                {"username": username.upper(), "password": "ClaveSegura123", "email": email, "registrationCode": code})
    check("registro con codigo correcto -> 201", s == 201 and b.get("roles") == ["ROLE_USER"])
    stu = b["token"]
    s, _ = call("POST", "/api/auth/login", {"username": username, "password": "ClaveSegura123"})
    check("login del estudiante (username en minusculas)", s == 200)
    s, _ = call("POST", "/api/auth/register",
                {"username": username + "x", "password": "ClaveSegura123", "email": email, "registrationCode": code})
    check("el codigo no se reutiliza -> 400", s == 400)

    # --- permisos del estudiante
    s, b = call("GET", "/api/students/me", token=stu)
    check("GET /students/me", s == 200 and b["email"] == email)
    for path in ("/api/students", f"/api/students/{sid}", "/api/payments", f"/api/payments/students/{sid}",
                 "/api/students/expired"):
        s, _ = call("GET", path, token=stu)
        check(f"estudiante GET {path} -> 403", s == 403)
    s, _ = call("POST", "/api/prices", {"type": "MONTHLY", "amount": 1, "durationDays": 1}, stu)
    check("estudiante no crea precios -> 403", s == 403)
    s, _ = call("POST", f"/api/payments/students/{sid}", {"type": "MONTHLY"}, stu)
    check("estudiante no registra pagos -> 403", s == 403)
    s, b = call("GET", "/api/prices/active", token=stu)
    check("estudiante ve precios vigentes", s == 200 and len(b) >= 1)

    # --- pagos simultaneos (valida el bloqueo de fila en la BD real)
    codes = []

    def pay():
        codes.append(call("POST", f"/api/payments/students/{sid}", {"type": "MONTHLY"}, adm)[0])

    threads = [threading.Thread(target=pay) for _ in range(PARALLEL)]
    [t.start() for t in threads]
    [t.join() for t in threads]
    check(f"{PARALLEL} pagos simultaneos: todos 201", codes.count(201) == PARALLEL, str(codes))
    s, b = call("GET", f"/api/students/{sid}", token=adm)
    exp1 = datetime.date.fromisoformat(b["expirationDate"])
    check(f"vencimiento = original + {PARALLEL}*30 dias", (exp1 - exp0).days == 30 * PARALLEL, f"+{(exp1 - exp0).days}")
    s, b = call("GET", "/api/payments/me", token=stu)
    check(f"historial propio con {PARALLEL} pagos", s == 200 and b["totalElements"] == PARALLEL)
    s, b = call("GET", f"/api/payments/students/{sid}?size=100&sort=periodStart", token=adm)
    periods = [(p["periodStart"], p["periodEnd"]) for p in b["content"]]
    check("periodos contiguos, sin solaparse",
          all(periods[i][1] == periods[i + 1][0] for i in range(len(periods) - 1)))

    # --- listados, busqueda y errores
    s, b = call("GET", "/api/students/expired", token=adm)
    check("GET /students/expired", s == 200)
    s, b = call("GET", "/api/students/expiring?days=2", token=adm)
    check("GET /students/expiring", s == 200)
    s, b = call("GET", f"/api/students?q=smoke-{stamp}", token=adm)
    check("busqueda ?q=", s == 200 and b["totalElements"] == 1)
    s, b = call("GET", "/api/students?size=100000", token=adm)
    check("size limitado a 100", s == 200 and b["size"] == 100)
    s, b = call("GET", "/api/students?sort=campoInexistente", token=adm)
    check("sort invalido -> 400", s == 400)
    s, b = call("GET", "/api/ruta-inexistente", token=adm)
    check("ruta inexistente -> 404 JSON", s == 404 and isinstance(b, dict) and b.get("code") == 404)
    s, b = call("POST", "/api/students", {"firstName": "", "email": "mal"}, adm)
    check("validacion -> 400 con errores por campo", s == 400 and "errors" in b)

    # --- recordatorios (no verifica el envio real de correo)
    s, b = call("POST", "/api/notifications/membership-reminders/run", token=adm)
    check("recordatorios: 202 started, o 409 already-running / mail-not-configured (sin correo)",
          s in (202, 409) and isinstance(b, dict)
          and b.get("status") in ("started", "already-running", "mail-not-configured"), str(b))

    # --- deshabilitar corta el acceso
    s, b = call("PATCH", f"/api/students/{sid}/deactivate", token=adm)
    check("deactivate", s == 200 and b["status"] == "INACTIVE")
    s, _ = call("GET", "/api/students/me", token=stu)
    check("token del deshabilitado deja de servir -> 401", s == 401)
    s, _ = call("POST", "/api/auth/login", {"username": username, "password": "ClaveSegura123"})
    check("login del deshabilitado -> 401", s == 401)

    bad = [n for n, ok in results if not ok]
    print(f"\nRESUMEN: {len(results) - len(bad)} OK / {len(results)} | fallas: {bad}")
    sys.exit(1 if bad else 0)


if __name__ == "__main__":
    main()
