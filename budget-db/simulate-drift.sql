-- Udaje normalne życie produkcji między dwoma uruchomieniami evala: użytkownik dopisał wydatki.
-- Uruchomienie: ./gradlew simulateDrift
insert into expense (spent_on, category, amount, description) values
    (date '2026-10-15', 'FOOD',      54.20, 'zakupy w Żabce'),
    (date '2026-10-15', 'TRANSPORT', 250.00, 'tankowanie na Orlenie');
