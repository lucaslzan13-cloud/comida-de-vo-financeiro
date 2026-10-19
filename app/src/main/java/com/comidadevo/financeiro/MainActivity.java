package com.comidadevo.financeiro;

import android.app.*;import android.os.*;import android.graphics.Color;import android.content.*;import android.view.*;import android.widget.*;import androidx.appcompat.app.AppCompatActivity;import com.google.android.gms.auth.api.signin.*;import com.google.android.gms.common.api.ApiException;import com.google.api.services.drive.DriveScopes;import java.text.*;import java.util.*;import java.util.concurrent.*;

public class MainActivity extends AppCompatActivity {
    static final int RC_SIGN_IN=7001, RC_PERM=7002;
    FinanceDb db; LinearLayout list; TextView inV,outV,balanceV,status; Spinner type,category; EditText amount,date,desc;
    ExecutorService exec=Executors.newSingleThreadExecutor();
    final String[] expenses={"Mercado","Açougue","Embalagens","Funcionários","Motoboy","Contas","Água","Luz","Internet","Combustível","Publicidade","iFood/Taxas","Equipamentos","Outros"};
    final String[] incomes={"Dinheiro","Cartão","Pix","Pix Online","iFood"};

    @Override public void onCreate(Bundle b){super.onCreate(b);db=new FinanceDb(this);buildUi();render();SyncWorker.schedule(this);}
    TextView tv(String s,int sp){TextView t=new TextView(this);t.setText(s);t.setTextSize(sp);t.setTextColor(Color.DKGRAY);t.setPadding(0,6,0,6);return t;}
    Button btn(String s){Button b=new Button(this);b.setText(s);return b;}

    public void buildUi(){
        ScrollView sv=new ScrollView(this); LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(18,18,18,28);sv.addView(root);setContentView(sv);
        TextView title=tv("Comida de Vó",25);title.setTextColor(Color.WHITE);title.setBackgroundColor(Color.rgb(17,24,39));title.setPadding(18,18,18,18);root.addView(title,new LinearLayout.LayoutParams(-1,-2));
        TextView sub=tv("Controle financeiro • funciona offline • backup automático no Google Drive",14);root.addView(sub);

        LinearLayout cards=new LinearLayout(this);cards.setOrientation(LinearLayout.HORIZONTAL);root.addView(cards);
        inV=tv("Entradas\nR$ 0,00",16);outV=tv("Gastos\nR$ 0,00",16);balanceV=tv("Saldo\nR$ 0,00",16);cards.addView(inV,weight());cards.addView(outV,weight());cards.addView(balanceV,weight());

        root.addView(tv("Novo lançamento",19));
        type=new Spinner(this);type.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"Gasto","Entrada"}));root.addView(type);
        amount=new EditText(this);amount.setHint("Valor (R$)");amount.setInputType(2|8192);root.addView(amount);
        category=new Spinner(this);root.addView(category);
        type.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){public void onNothingSelected(android.widget.AdapterView<?> p){} public void onItemSelected(android.widget.AdapterView<?> p,View v,int pos,long id){setCategories(pos==1);}});setCategories(false);
        date=new EditText(this);date.setHint("Data (AAAA-MM-DD)");date.setText(new SimpleDateFormat("yyyy-MM-dd",Locale.US).format(new Date()));root.addView(date);
        desc=new EditText(this);desc.setHint("Descrição");root.addView(desc);
        Button add=btn("Adicionar lançamento");root.addView(add);add.setOnClickListener(v->addEntry());

        root.addView(tv("Google Drive",19));
        TextView driveInfo=tv("Se conectado, os lançamentos são mesclados com a nuvem e o backup fica na pasta 'Comida de Vó - Financeiro' do seu Google Drive.",13);root.addView(driveInfo);
        LinearLayout syncRow=new LinearLayout(this);
        Button google=btn("Conectar Google");
        Button sync=btn("Sincronizar agora");
        status=tv("Não conectado",13);
        syncRow.addView(google,new LinearLayout.LayoutParams(0,-2,1));
        syncRow.addView(sync,new LinearLayout.LayoutParams(0,-2,1));
        root.addView(syncRow);root.addView(status);
        google.setOnClickListener(v->connectGoogle());
        sync.setOnClickListener(v->syncSoon(true));

        root.addView(tv("Histórico",19));
        LinearLayout filters=new LinearLayout(this);Spinner month=new Spinner(this);String[] months={"Todos os meses",new SimpleDateFormat("yyyy-MM",Locale.US).format(new Date())};month.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,months));filters.addView(month,new LinearLayout.LayoutParams(0,-2,1));root.addView(filters);
        list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);root.addView(list);
    }

    LinearLayout.LayoutParams weight(){return new LinearLayout.LayoutParams(0,100,1);}
    void setCategories(boolean income){category.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,income?incomes:expenses));}

    void addEntry(){try{double a=Double.parseDouble(amount.getText().toString().replace(',','.'));if(a<=0)throw new Exception();String id=UUID.randomUUID().toString();db.upsert(id,type.getSelectedItemPosition()==1?"in":"out",a,category.getSelectedItem().toString(),date.getText().toString().trim(),desc.getText().toString().trim(),System.currentTimeMillis(),false);clearForm();render();syncSoon(false);}catch(Exception e){Toast.makeText(this,"Informe um valor válido.",Toast.LENGTH_SHORT).show();}}
    void clearForm(){amount.setText("");desc.setText("");date.setText(new SimpleDateFormat("yyyy-MM-dd",Locale.US).format(new Date()));}

    void render(){if(list==null)return;List<FinanceDb.Entry> es=db.all();double in=0,out=0;list.removeAllViews();for(FinanceDb.Entry e:es){if(e.deleted)continue;if("in".equals(e.type))in+=e.amount;else out+=e.amount;LinearLayout row=new LinearLayout(this);row.setPadding(0,12,0,12);row.setOrientation(LinearLayout.VERTICAL);TextView a=tv(("in".equals(e.type)?"+ ":"− ")+money(e.amount)+"  •  "+e.category,16);TextView d=tv(e.date+"  •  "+(e.description==null||e.description.isEmpty()?e.category:e.description),13);row.addView(a);row.addView(d);row.setOnClickListener(v->edit(e));row.setOnLongClickListener(v->{delete(e);return true;});list.addView(row,new LinearLayout.LayoutParams(-1,-2));}inV.setText("Entradas\n"+money(in));outV.setText("Gastos\n"+money(out));balanceV.setText("Saldo\n"+money(in-out));}
    String money(double n){return NumberFormat.getCurrencyInstance(new Locale("pt","BR")).format(n);}

    void edit(FinanceDb.Entry e){LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);EditText v=new EditText(this);v.setText(String.valueOf(e.amount));EditText d=new EditText(this);d.setText(e.description);box.addView(v);box.addView(d);new AlertDialog.Builder(this).setTitle("Editar lançamento").setView(box).setPositiveButton("Salvar",(x,w)->{try{double a=Double.parseDouble(v.getText().toString().replace(',','.'));db.upsert(e.id,e.type,a,e.category,e.date,d.getText().toString(),System.currentTimeMillis(),false);render();syncSoon(false);}catch(Exception ignored){}}).setNegativeButton("Cancelar",null).setNeutralButton("Excluir",(x,w)->delete(e)).show();}
    void delete(FinanceDb.Entry e){new AlertDialog.Builder(this).setTitle("Excluir lançamento?").setMessage("Ele será marcado como excluído e essa alteração será enviada ao backup.").setPositiveButton("Excluir",(d,w)->{db.upsert(e.id,e.type,e.amount,e.category,e.date,e.description,System.currentTimeMillis(),true);render();syncSoon(false);}).setNegativeButton("Cancelar",null).show();}

    boolean hasDrivePermission(GoogleSignInAccount a){return a!=null && GoogleSignIn.hasPermissions(a,new com.google.android.gms.common.api.Scope(DriveScopes.DRIVE_FILE));}

    void syncSoon(boolean showToast){
        exec.execute(()->{
            try{
                GoogleSignInAccount a=GoogleSignIn.getLastSignedInAccount(this);
                if(a==null){runOnUiThread(()->status.setText("Não conectado — toque em Conectar Google"));if(showToast)runOnUiThread(()->Toast.makeText(this,"Conecte uma conta Google primeiro.",Toast.LENGTH_SHORT).show());return;}
                if(!hasDrivePermission(a)){runOnUiThread(()->connectGoogle());return;}
                runOnUiThread(()->status.setText("Sincronizando..."));
                new SyncManager(this).sync(a);
                runOnUiThread(()->{status.setText("Google Drive: backup atualizado");render();if(showToast)Toast.makeText(this,"Backup atualizado com sucesso.",Toast.LENGTH_SHORT).show();});
            }catch(Exception e){runOnUiThread(()->{status.setText("Google Drive: aguardando conexão");if(showToast)Toast.makeText(this,"Não foi possível sincronizar agora. Os dados locais continuam salvos.",Toast.LENGTH_SHORT).show();});}
        });
    }

    void connectGoogle(){
        GoogleSignInAccount a=GoogleSignIn.getLastSignedInAccount(this);
        if(a!=null){
            if(!hasDrivePermission(a)){
                GoogleSignIn.requestPermissions(this,RC_PERM,a,new com.google.android.gms.common.api.Scope(DriveScopes.DRIVE_FILE));
            }else syncSoon(true);
            return;
        }
        GoogleSignInOptions gso=new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .requestScopes(new com.google.android.gms.common.api.Scope(DriveScopes.DRIVE_FILE))
                .build();
        startActivityForResult(GoogleSignIn.getClient(this,gso).getSignInIntent(),RC_SIGN_IN);
    }

    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if(request==RC_SIGN_IN){
            try{
                GoogleSignInAccount a=GoogleSignIn.getSignedInAccountFromIntent(data).getResult(ApiException.class);
                if(a!=null && hasDrivePermission(a)){status.setText("Google conectado — preparando backup");syncSoon(true);}
                else status.setText("Google conectado, mas o acesso ao Drive não foi concedido");
            }catch(Exception e){status.setText("Login cancelado");}
        }else if(request==RC_PERM){syncSoon(true);}
    }

    @Override protected void onDestroy(){exec.shutdownNow();db.close();super.onDestroy();}
}
