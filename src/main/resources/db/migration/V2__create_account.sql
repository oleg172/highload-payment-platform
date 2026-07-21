create table account
(
    id uuid primary key,
    number varchar(32) not null unique,
    balance numeric(19,2) not null,
    created_at timestamp not null,
    version bigint not null
);