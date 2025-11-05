package org.example;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import jakarta.persistence.TypedQuery;
import org.example.entity.Usuario;
import java.io.*;
import java.net.*;
import java.security.Key;
import java.util.ArrayList;
import java.util.Date;
//erro de tamanho da senha
//deslogar usuario ao excluilo

public class EchoServerTCP_Server {

    private static final Gson gson = new Gson();
    private static final int MAX_REQUEST_SIZE = 2048;
    private static final Key chaveSecreta = Keys.secretKeyFor(SignatureAlgorithm.HS256);
    private static final String USERNAME_REGEX = "^[a-z0-9_]{3,15}$";

    public static void main(String args[]) throws IOException {
        ServerSocket echoServer = null;
        Socket clientSocket = null;

        System.out.println("Qual porta o servidor deve usar? ");
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
        int porta = Integer.parseInt(br.readLine());

        System.out.println("Servidor carregado na porta " + porta);

        try {
            echoServer = new ServerSocket(porta);
        } catch (IOException e) {
            System.out.println(e);
            return;
        }

        while (true) {
            DataInputStream is = null;
            PrintStream os = null;

            try {
                System.out.println("...Aguardando conexão...");
                clientSocket = echoServer.accept();
                System.out.println("Cliente conectado: " + clientSocket.getInetAddress().getHostAddress());

                is = new DataInputStream(clientSocket.getInputStream());
                os = new PrintStream(clientSocket.getOutputStream());

                boolean keepConnectionAlive = true;

                while (keepConnectionAlive) {
                    String line = is.readLine();

                    if (line == null) {
                        System.out.println("Cliente desconectou,  a conexao foi perdida");
                        break;
                    }

                    if (line.length() > MAX_REQUEST_SIZE) {
                        sendResponse(os, "413", "A requisicao excede o tamanho maximo permitido.", null);
                        continue;
                    }

                    System.out.println("\n[CLIENTE -> SERVIDOR]");
                    System.out.println(line);

                    JsonObject requisicaoJson;
                    try {
                        requisicaoJson = JsonParser.parseString(line).getAsJsonObject();
                    } catch (JsonSyntaxException | IllegalStateException e) {
                        sendResponse(os, "400", "A requisicao nao e um JSON valido.", null);
                        continue;
                    }

                    String operacao = requisicaoJson.get("operacao").getAsString();

                    if (!checarOperacao(operacao) || !requisicaoJson.has("operacao")) {
                        sendResponse(os, "400", "Erro: Operação não encontrada ou inválida" + operacao, null);
                        continue;
                    }

                    switch (operacao) {
                        case "CRIAR_USUARIO":
                            handleCreateUser(requisicaoJson, os);
                            break;

                        case "LOGIN":
                            handleLogin(requisicaoJson, os);
                            break;

                        case "LISTAR_PROPRIO_USUARIO":
                            handleListUser(requisicaoJson, os);
                            break;

                        case "EDITAR_PROPRIO_USUARIO":
                            handleEditUser(requisicaoJson, os);
                            break;

                        case "EXCLUIR_PROPRIO_USUARIO":
                            handleDeleteUser(requisicaoJson, os);
                            System.out.println("Usuário excluído. Servidor encerrando esta conexão.");
                            keepConnectionAlive = false;
                            break;

                        case "LOGOUT":
                            handleLogout(requisicaoJson, os);
                            System.out.println("Cliente desconectou (solicitou LOGOUT ou conexao foi perdida).");
                            keepConnectionAlive = false;
                            break;

                        default:
                            sendResponse(os, "400", "Operacao desconhecida.", null);
                            break;
                    }
                }
            } catch (IOException e) {
                System.err.println("Erro na comunicacao com o cliente: " + e.getMessage());
            } finally {
                System.out.println("Fechando conexao com o cliente.");
                try {
                    if (os != null) os.close();
                    if (is != null) is.close();
                    if (clientSocket != null) clientSocket.close();
                } catch (IOException e) {
                    System.err.println("Erro ao fechar socket do cliente: " + e.getMessage());
                }
            }
        }
    }

    private static boolean checarOperacao (String operacao){
        ArrayList<String> operacoes = new ArrayList<String>();
        operacoes.add("CRIAR_USUARIO");
        operacoes.add("LOGIN");
        operacoes.add("LOGOUT");
        operacoes.add("LISTAR_PROPRIO_USUARIO");
        operacoes.add("EDITAR_PROPRIO_USUARIO");
        operacoes.add("EXCLUIR_PROPRIO_USUARIO");
        return operacoes.contains(operacao);
    }

    private static void handleCreateUser(JsonObject req, PrintStream os) {
        if (!req.has("usuario") || !req.get("usuario").isJsonObject() || !req.getAsJsonObject("usuario").has("nome") || !req.getAsJsonObject("usuario").has("senha")) {
            sendResponse(os, "405", "Erro: Campos inválidos, verifique o tipo e quantidade de caracteres e o objeto usuario", null);
            return;
        }
        String nome = req.getAsJsonObject("usuario").get("nome").getAsString();
        String senha = req.getAsJsonObject("usuario").get("senha").getAsString();


        if (nome.trim().isEmpty() || senha.trim().isEmpty()) {
            sendResponse(os, "422", "Nome de usuario e senha nao podem ser vazios.", null);
            return;
        }

        if (!nome.matches(USERNAME_REGEX)) {
            sendResponse(os, "405", "Nome de usuario invalido. Use apenas letras minusculas, numeros e '_', de 3 a 15 caracteres.", null);
            return;
        }

        if (senha.length() < 3 || senha.length()>20) {
            sendResponse(os, "405", "Senha invalida. deve ter de 3 e 20 caracteres", null);
            return;
        }

        EntityManager em = JpaUtil.getEntityManager();
        try {
            TypedQuery<Long> query = em.createQuery("SELECT COUNT(u) FROM Usuario u WHERE u.nome = :nome", Long.class);
            query.setParameter("nome", nome);
            if (query.getSingleResult() > 0) {
                sendResponse(os, "409", "Este nome de usuario ja esta em uso.", null);
                return;
            }
            Usuario novoUsuario = new Usuario(nome, senha);
            em.getTransaction().begin();
            em.persist(novoUsuario);
            em.getTransaction().commit();
            sendResponse(os, "201", "Usuario criado com sucesso.", null);
        } catch (Exception e) {
            if (em.getTransaction().isActive()) em.getTransaction().rollback();
            sendResponse(os, "500", "Erro interno no servidor.", null);
        } finally {
            em.close();
        }
    }

    private static void handleListUser(JsonObject req, PrintStream os) {
        if (!req.has("token")) {
            sendResponse(os, "400", "O campo 'token' é obrigatório.", null);
            return;
        }

        String nomeUsuario = validarTokenEObterNomeUsuario(req.get("token").getAsString());

        if (nomeUsuario == null) {
            sendResponse(os, "401", "Token inválido ou expirado.", null);
            return;
        }

        JsonObject resposta = new JsonObject();
        resposta.addProperty("status", "200");
        resposta.addProperty("usuario", nomeUsuario);
        resposta.addProperty("mensagem", "Sucesso: operação realizada com sucesso");
        String jsonParaEnviar = gson.toJson(resposta);
        System.out.println("[SERVIDOR -> CLIENTE]");
        System.out.println(jsonParaEnviar);
        os.println(jsonParaEnviar);

    }

    private static void handleLogin(JsonObject req, PrintStream os) {
        if (!req.has("usuario") || !req.has("senha")) {
            sendResponse(os, "422", "Erro: Chaves faltantes ou invalidas", null);
            return;
        }
        String nome = req.get("usuario").getAsString();

        String senha = req.get("senha").getAsString();

        EntityManager em = JpaUtil.getEntityManager();
        try {
            TypedQuery<Usuario> query = em.createQuery("SELECT u FROM Usuario u WHERE u.nome = :nome", Usuario.class);
            query.setParameter("nome", nome);
            Usuario user = query.getSingleResult();
            if (!user.getSenha().equals(senha)) {
                sendResponse(os, "401", "Credenciais invalidas.", null);
                return;
            }
            String token = gerarTokenJWT(user);
            sendResponse(os, "200", "usuario logado", token);
        } catch (NoResultException e) {
            sendResponse(os, "401", "Credenciais invalidas.", null);
        } finally {
            em.close();
        }
    }

    private static void handleLogout(JsonObject req, PrintStream os) {
        if (!req.has("token")) {
            sendResponse(os, "422", "O campo 'token' e obrigatorio.", null);
            return;
        }
        if (validarTokenEObterNomeUsuario(req.get("token").getAsString()) != null) {
            sendResponse(os, "200", "Logout realizado com sucesso.", null);
        } else {
            sendResponse(os, "401", "Token inválido ou expirado.", null);
        }
    }

    private static void handleEditUser(JsonObject req, PrintStream os) {
        if (!req.has("token") || !req.has("usuario") || !req.getAsJsonObject("usuario").has("senha")) {
            sendResponse(os, "400", "Os campos token e o objeto usuario com senha sao obrigatorios.", null);
            return;
        }
        String nomeUsuario = validarTokenEObterNomeUsuario(req.get("token").getAsString());
        if (nomeUsuario == null) {
            sendResponse(os, "401", "Token invalido ou expirado.", null);
            return;
        }
        String novaSenha = req.getAsJsonObject("usuario").get("senha").getAsString();

        if (novaSenha.length() < 3 || novaSenha.length()>20 || novaSenha.trim().isEmpty()) {
            sendResponse(os, "405", "Senha invalida. deve ter de 3 e 20 caracteres", null);
            return;
        }

        EntityManager em = JpaUtil.getEntityManager();

        try {
            em.getTransaction().begin();
            TypedQuery<Usuario> query = em.createQuery("SELECT u FROM Usuario u WHERE u.nome = :nome", Usuario.class);
            query.setParameter("nome", nomeUsuario);
            Usuario user = query.getSingleResult();
            user.setSenha(novaSenha);
            em.getTransaction().commit();
            sendResponse(os, "200", "usuario atualizado com sucesso.", null);
        } catch (NoResultException e) {
            sendResponse(os, "404", "usuario do token não encontrado.", null);
        } finally {
            if (em.getTransaction().isActive()) em.getTransaction().rollback();
            em.close();
        }
    }

    private static void handleDeleteUser(JsonObject req, PrintStream os) {
        if (!req.has("token")) {
            sendResponse(os, "422", "O campo 'token' e obrigatorio.", null);
            return;
        }
        String nomeUsuario = validarTokenEObterNomeUsuario(req.get("token").getAsString());
        if (nomeUsuario == null) {
            sendResponse(os, "401", "Token invalido.", null);
            return;
        }
        EntityManager em = JpaUtil.getEntityManager();
        try {
            em.getTransaction().begin();
            TypedQuery<Usuario> query = em.createQuery("SELECT u FROM Usuario u WHERE u.nome = :nome", Usuario.class);
            query.setParameter("nome", nomeUsuario);
            Usuario user = query.getSingleResult();
            em.remove(user);
            em.getTransaction().commit();
            sendResponse(os, "200", "usuario excluido com sucesso.", null);
        } catch (NoResultException e) {
            sendResponse(os, "404", "usuario do token nao encontrado.", null);
        } finally {
            if (em.getTransaction().isActive()) em.getTransaction().rollback();
            em.close();
        }
    }

    private static String gerarTokenJWT(Usuario usuario) {
        long agoraMillis = System.currentTimeMillis();
        Date agora = new Date(agoraMillis);
        Date dataExpiracao = new Date(agoraMillis + 3600000); // Expira em 1 hora

        return Jwts.builder()
                .setSubject(usuario.getNome())
                .claim("id", usuario.getId())
                .claim("nome", usuario.getNome())
                .claim("funcao", "user")
                .setIssuedAt(agora)
                .setExpiration(dataExpiracao)
                .signWith(chaveSecreta)
                .compact();
    }

    private static String validarTokenEObterNomeUsuario(String token) {
        try {
            Jws<Claims> claimsJws = Jwts.parser()
                    .setSigningKey(chaveSecreta)
                    .build()
                    .parseClaimsJws(token);
            return claimsJws.getBody().getSubject();
        } catch (Exception e) {
            System.err.println("Erro na validacao do Token: " + e.getMessage());
            return null;
        }
    }

    private static void sendResponse(PrintStream os, String status, String mensagem, String token) {
        JsonObject resposta = new JsonObject();
        resposta.addProperty("status", status);
        if (mensagem != null) {
            resposta.addProperty("mensagem", mensagem);
        }
        if (token != null) {
            resposta.addProperty("token", token);
        }
        String jsonParaEnviar = gson.toJson(resposta);
        System.out.println("[SERVIDOR -> CLIENTE]");
        System.out.println(jsonParaEnviar);
        os.println(jsonParaEnviar);
    }
}