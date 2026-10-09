-- V3: el login y el registro normalizan usernames y emails a minusculas (ver D-016).
-- Las bases creadas antes de esa regla pueden tener usuarios como "Salito" que ya no podrian entrar.
-- Si dos filas colisionaran al pasar a minusculas, la restriccion UNIQUE hace fallar la migracion
-- (a proposito: se resuelve a mano, no se pierde ni se pisa ningun dato).
update users set username = lower(trim(username)) where username <> lower(trim(username));
update students set email = lower(trim(email)) where email <> lower(trim(email));
