package com.comidadevo.financeiro;

import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.os.*;
import android.view.*;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.gms.auth.api.identity.AuthorizationRequest;
import com.google.android.gms.auth.api.identity.AuthorizationResult;
import com.google.android.gms.auth.api.identity.Identity;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.common.api.Scope;
import com.google.api.services.drive.DriveScopes;
import java.text.*;
import java.util.*;
import java.util.concurrent.*;

public class MainActivity extends AppCompatActivity {
    static final int RC_AUTH=7003;
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
        date=new EditText(this);date.setHint("Data (AAAA-MM-DD)");date.setText(today());root.addView(date);
        desc=new EditText(this);desc.setHint("Descrição");root.addView(desc);
        Button add=btn("Adicionar lançamento");root.addView(add);add.setOnClickListener(v->addEntry());

        root.addView(tv("Google Drive",19));
        TextView driveInfo=tv("O backup fica na pasta 'Comida de Vó - Financeiro' do seu Google Drive. Os dados são salvos localmente primeiro e sincronizados quando houver internet.",13);root.addView(driveInfo);
        LinearLayout syncRow=new LinearLayout(this);
        Button google=btn("Conectar Google");
        Button sync=btn("Sincronizar agora");
        status=tv("Não conectado",13);
        syncRow.addView(google,new LinearLayout.LayoutParams(0,-2,1));
        syncRow.addView(sync,new LinearLayout.LayoutParams(0,-2,1));
        root.addView(syncRow);root.addView(status);
        google.setOnClickListener(v->connectGoogle());
        sync.setOnClickListener(v->requestDriveAuthorization(true));

        root.addView(tv("Histórico",19));
        LinearLayout filters=new LinearLayout(this);Spinner month=new Spinner(this);String[] months={"Todos os meses",today().substring(0,7)};month.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,months));filters.addView(month,new LinearLayout.LayoutParams(0,-2,1));root.addView(filters);
        list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);root.addView(list);
    }

    LinearLayout.LayoutParams weight(){return new LinearLayout.LayoutParams(0,100,1);}
    void setCategories(boolean income){category.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,income?incomes:expenses));}
    String today(){return new SimpleDateFormat("yyyy-MM-dd",Locale.US).format(new Date());}

    void addEntry(){
        try{
            String raw=amount.getText().toString().trim().replace("R$","").replace(".","").replace(',','.').trim();
            double a=Double.parseDouble(raw);
            if(a<=0 || a>100000000)throw new Exception();
            String d=date.getText().toString().trim();
            if(!d.matches("\\d{4}-\\d{2}-\\d{2}"))throw new Exception();
            String id=UUID.randomUUID().toString();
            db.upsert(id,type.getSelectedItemPosition()==1?"in":"out",a,category.getSelectedItem().toString(),d,desc.getText().toString().trim(),System.currentTimeMillis(),false);
            clearForm();render();syncSoon(false);
        }catch(Exception e){Toast.makeText(this,"Confira valor e data do lançamento.",Toast.LENGTH_SHORT).show();}
    }
    void clearForm(){amount.setText("");desc.setText("");date.setText(today());}

    void render(){
        if(list==null)return;
        List<FinanceDb.Entry> es=db.all();double in=0,out=0;list.removeAllViews();
        for(FinanceDb.Entry e:es){
            if(e.deleted)continue;
            if("in".equals(e.type))in+=e.amount;else out+=e.amount;
            LinearLayout row=new LinearLayout(this);row.setPadding(0,12,0,12);row.setOrientation(LinearLayout.VERTICAL);
            TextView a=tv(("in".equals(e.type)?"+ ":"− ")+money(e.amount)+"  •  "+e.category,16);
            TextView d=tv(e.date+"  •  "+(e.description==null||e.description.isEmpty()?e.category:e.description),13);
            row.addView(a);row.addView(d);row.setOnClickListener(v->edit(e));row.setOnLongClickListener(v->{delete(e);return true;});list.addView(row,new LinearLayout.LayoutParams(-1,-2));
        }
        inV.setText("Entradas\n"+money(in));outV.setText("Gastos\n"+money(out));balanceV.setText("Saldo\n"+money(in-out));
    }
    String money(double n){return NumberFormat.getCurrencyInstance(new Locale("pt","BR")).format(n);}

    void edit(FinanceDb.Entry e){
        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);
        EditText v=new EditText(this);v.setText(String.valueOf(e.amount));v.setInputType(2|8192);
        EditText d=new EditText(this);d.setText(e.description==null?"":e.description);
        box.addView(v);box.addView(d);
        new AlertDialog.Builder(this).setTitle("Editar lançamento").setView(box).setPositiveButton("Salvar",(x,w)->{
            try{double a=Double.parseDouble(v.getText().toString().replace(',','.'));if(a<=0)throw new Exception();db.upsert(e.id,e.type,a,e.category,e.date,d.getText().toString(),System.currentTimeMillis(),false);render();syncSoon(false);}catch(Exception ignored){Toast.makeText(this,"Valor inválido.",Toast.LENGTH_SHORT).show();}
        }).setNegativeButton("Cancelar",null).setNeutralButton("Excluir",(x,w)->delete(e)).show();
    }
    void delete(FinanceDb.Entry e){new AlertDialog.Builder(this).setTitle("Excluir lançamento?").setMessage("Ele será marcado como excluído e essa alteração será enviada ao backup.").setPositiveButton("Excluir",(d,w)->{db.upsert(e.id,e.type,e.amount,e.category,e.date,e.description,System.currentTimeMillis(),true);render();syncSoon(false);}).setNegativeButton("Cancelar",null).show();}

    void requestDriveAuthorization(boolean showToast){
        try{
            List<Scope> scopes=Collections.singletonList(new Scope(DriveScopes.DRIVE_FILE));
            AuthorizationRequest request=AuthorizationRequest.builder().setRequestedScopes(scopes).build();
            status.setText("Verificando acesso ao Google Drive...");
            Identity.getAuthorizationClient(this).authorize(request)
                    .addOnSuccessListener(result->{
                        if(result.hasResolution()){
                            try{startIntentSenderForResult(result.getPendingIntent().getIntentSender(),RC_AUTH,null,0,0,0,null);}
                            catch(Exception e){status.setText("Não foi possível abrir a autorização");}
                        }else syncWithToken(result.getAccessToken(),showToast);
                    })
                    .addOnFailureListener(e->{status.setText("Google Drive não conectado");if(showToast)Toast.makeText(this,"Não foi possível autorizar o Google Drive.",Toast.LENGTH_SHORT).show();});
        }catch(Exception e){status.setText("Google Drive indisponível");if(showToast)Toast.makeText(this,"Verifique se o Google Play Services está atualizado.",Toast.LENGTH_SHORT).show();}
    }

    void connectGoogle(){requestDriveAuthorization(true);}
    void syncSoon(boolean showToast){requestDriveAuthorization(showToast);}

    void syncWithToken(String token,boolean showToast){
        if(token==null || token.isEmpty()){
            status.setText("Google Drive: autorização incompleta");
            if(showToast)Toast.makeText(this,"Autorize o Google Drive para fazer o backup.",Toast.LENGTH_SHORT).show();
            return;
        }
        exec.execute(()->{
            try{
                runOnUiThread(()->status.setText("Sincronizando..."));
                new SyncManager(this).sync(token);
                runOnUiThread(()->{status.setText("Google Drive: backup atualizado");render();if(showToast)Toast.makeText(this,"Backup atualizado com sucesso.",Toast.LENGTH_SHORT).show();});
            }catch(Exception e){
                runOnUiThread(()->{status.setText("Google Drive: falha na sincronização");if(showToast)Toast.makeText(this,"Não foi possível sincronizar agora. Os dados locais continuam salvos.",Toast.LENGTH_SHORT).show();});
            }
        });
    }

    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if(request==RC_AUTH){
            try{
                AuthorizationResult authorizationResult=Identity.getAuthorizationClient(this).getAuthorizationResultFromIntent(data);
                if(authorizationResult!=null && authorizationResult.getAccessToken()!=null) syncWithToken(authorizationResult.getAccessToken(),true);
                else status.setText("Autorização do Google Drive cancelada");
            }catch(ApiException e){status.setText("Autorização do Google Drive cancelada");}
            catch(Exception e){status.setText("Erro na autorização do Google Drive");}
        }
    }

    @Override protected void onDestroy(){exec.shutdownNow();db.close();super.onDestroy();}
}
